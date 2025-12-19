package com.boda.canteen.controller;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boda.canteen.common.MyTimeUtils;
import com.boda.canteen.entity.*;
import com.boda.canteen.security.service.BlanketOrderService;
import com.boda.canteen.security.service.MenuService;
import com.boda.canteen.security.service.OrderFormService;
import com.boda.canteen.security.service.ShopCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;


/**
 * 前台业务加视图控制器
 */
@Slf4j
@Controller
@RequestMapping("/front")
public class FrontController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private ShopCartService shopCartService;

    @Autowired
    private OrderFormService orderFormService;

    @Autowired
    private BlanketOrderService blanketOrderService;

    /**
     * 跳转登出页面（即登录页面）
     */
    @GetMapping("/logout")
    public String logout(HttpServletRequest request){
        SecurityContextHolder.clearContext();
        request.getSession().removeAttribute("currUser");
        return "login";
    }

    /**
     * 跳转首页
     */
    @GetMapping("/toMain")
    public String toMain(HttpServletRequest request){
        LambdaQueryWrapper<Menu> queryWrapper = new LambdaQueryWrapper<>();

        // 获取当前时间的一周开始与一周结束
        Date weekOfBeginTime = MyTimeUtils.getDayOfBeginTime();
        Date weekOfEndTime = MyTimeUtils.getDayOfEndTime();

        queryWrapper.between(Menu::getCreateTime, weekOfBeginTime, weekOfEndTime)
                .orderByDesc(Menu::getCreateTime);

        List<Menu> menuList = menuService.list(queryWrapper);
        request.getSession().setAttribute("menuList", menuList);

        return "front/main";
    }

    /**
     * 跳转菜品详情页面
     */
    @GetMapping("/toDetail/{menuId}")
    public String toDetail(@PathVariable Long menuId, HttpServletRequest request){
        Menu menuDetail = menuService.getById(menuId);
        request.getSession().setAttribute("menuDetail", menuDetail);
        return "front/detail";
    }

    /**
     * 跳转购物车页面
     */
    @GetMapping("/toShopCart")
    public String toShopCart(){
        return "front/shopcart";
    }

    /**
     * 跳转个人信息页面
     */
    @GetMapping("/toInfo")
    public String toInfo(HttpServletRequest request) {
        // 重定向到用户中心页面
        return "redirect:/front/toUserCenter";
    }

    /**
     * 跳转订单页面
     */
    @GetMapping("/toOrder")
    public String toOrder(HttpServletRequest request) {
        // 重定向到用户中心页面
        return "redirect:/front/toUserCenter";
    }

    /**
     * 跳转确认订单页面
     */
    @GetMapping("/toOrderConfirm")
    public String toOrderConfirm(HttpServletRequest request) {
        MyUser user = (MyUser) request.getSession().getAttribute("currUser");
        Long userId = user.getUserId();
        LambdaQueryWrapper<ShopCart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShopCart::getUserId, userId);
        List<ShopCart> shopCartList = shopCartService.list(queryWrapper);
        long totalPrice = 0;
        long totalWeight = 0;
        for (ShopCart sc : shopCartList){
            totalWeight += sc.getWeight();
            totalPrice += sc.getTotalPrice();
        }
        request.getSession().setAttribute("shopCartList", shopCartList);
        request.getSession().setAttribute("totalPrice", totalPrice);
        request.getSession().setAttribute("totalWeight", totalWeight);
        return "front/orderConfirm";
    }

    /**
     * 跳转月度订单页面
     */
    @GetMapping("/toMonOrder")
    public String toMonOrder(HttpServletRequest request){
        // 重定向到用户中心页面
        return "redirect:/front/toUserCenter";
    }

    /**
     * 跳转用户中心页面
     */
    @GetMapping("/toUserCenter")
    public String toUserCenter(HttpServletRequest request){
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        Long userId = currUser.getUserId();
        String workInformation = currUser.getWorkInformation();
        request.getSession().setAttribute("workInformation", workInformation);

        // 加载今日订单数据
        Date begin = DateUtil.beginOfDay(DateUtil.date());
        Date end = DateUtil.endOfDay(DateUtil.date());
        LambdaQueryWrapper<OrderForm> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderForm::getUserId, userId);
        queryWrapper.between(OrderForm::getOrderTime, begin, end);
        List<OrderForm> orderList = orderFormService.list(queryWrapper);
        request.getSession().setAttribute("orderList", orderList);

        // 查询所有总括订单
        List<BlanketOrder> blanketOrderList = blanketOrderService.list();
        request.getSession().setAttribute("blanketOrderList", blanketOrderList);

        // 加载月度订单数据
        String monDate = DateUtil.formatDate(DateUtil.date()).substring(0, 7);
        request.getSession().setAttribute("monDate", monDate);
        String month = monDate + "-01";
        Date monBegin = MyTimeUtils.getMonthOfBeginTime(month);
        Date monEnd = MyTimeUtils.getMonthOfEndTime(month);

        LambdaQueryWrapper<OrderForm> monQueryWrapper = new LambdaQueryWrapper<>();
        monQueryWrapper.eq(OrderForm::getUserId, userId)
                .between(OrderForm::getOrderTime, monBegin, monEnd);
        List<OrderForm> monOrderList = orderFormService.list(monQueryWrapper);

        // 统计月度总金额
        Long monTotalPrice = 0L;
        List<BlanketOrder> monBlanketOrderList = new ArrayList<>();
        for (OrderForm orderForm : monOrderList){
            monTotalPrice += orderForm.getOrderPrice();
            LambdaQueryWrapper<BlanketOrder> blanketOrderLambdaQueryWrapper = new LambdaQueryWrapper<>();
            blanketOrderLambdaQueryWrapper.eq(BlanketOrder::getOrderId, orderForm.getOrderId());
            monBlanketOrderList.addAll(blanketOrderService.list(blanketOrderLambdaQueryWrapper));
        }
        request.getSession().setAttribute("monBlanketOrderList", monBlanketOrderList);
        request.getSession().setAttribute("monTotalPrice", monTotalPrice);

        return "front/userCenter";
    }

    /**
     * 月度订单打印
     */
    @GetMapping("/print")
    public void print(HttpServletResponse response, HttpServletRequest request){
        // 获取到查询出的用户信息、月份、总价格、总括订单信息
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        String monDate = (String) request.getSession().getAttribute("monDate");
        Long monTotalPrice = (Long) request.getSession().getAttribute("monTotalPrice");
        List<BlanketOrder> monBlanketOrderList = (List<BlanketOrder>) request.getSession().getAttribute("monBlanketOrderList");
        // 声明输出流
        ServletOutputStream out = null;
        try (ExcelWriter writer = ExcelUtil.getWriter()) {
            // 封装格式
            writer.merge(4, "员工月度订单汇总");
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("员工", currUser.getName());
            map.put("", "");
            map.put("联系电话", currUser.getTelephone());
            map.put(" ", " ");
            map.put("统计月份", monDate);
            ArrayList<Map<String, Object>> list1 = new ArrayList<>();
            list1.add(map);
            writer.write(list1, true);
            ArrayList<Map<String, Object>> list2 = new ArrayList<>();
            for (BlanketOrder bo : monBlanketOrderList){
                Map<String, Object> map1 = new LinkedHashMap<>();
                map1.put("时间", DateUtil.formatDateTime(bo.getCreateTime()));
                map1.put("菜名", bo.getName());
                map1.put("单位", bo.getUnit());
                map1.put("分量", bo.getWeight());
                map1.put("总计", bo.getTotalPrice());
                list2.add(map1);
            }
            writer.write(list2, true);
            writer.merge(4, "合计金额");
            writer.merge(4, monTotalPrice, false);
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=monOrder.xls");
            out = response.getOutputStream();
            writer.flush(out, true);    // 输出
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                IoUtil.close(out);
            }
        }
    }
}