package com.boda.canteen.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.boda.canteen.common.R;
import com.boda.canteen.entity.MyUser;
import com.boda.canteen.entity.ShopCart;
import com.boda.canteen.exception.CustomException;
import com.boda.canteen.security.service.ShopCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/shopCart")
public class ShopCartController {
    @Autowired
    private ShopCartService shopCartService;

    /**
     * 购物车添加接口（修复多用户冲突问题）
     */
    @PostMapping("/add")
    public R<String> saveInfo(@RequestBody ShopCart shopCart, HttpServletRequest request){
        if (shopCart == null) {
            throw new CustomException("购物车基本信息为空,无法加入购物车");
        }

        // 1. 获取当前登录用户（必须先校验用户登录状态）
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        if (currUser == null || currUser.getUserId() == null) {
            throw new CustomException("用户未登录，无法加入购物车");
        }
        Long userId = currUser.getUserId();

        boolean res;
        String name = shopCart.getName();

        // 2. 核心修改：查询条件增加用户ID，确保只查询当前用户的该菜品记录
        LambdaQueryWrapper<ShopCart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShopCart::getName, name)  // 菜品名称
                .eq(ShopCart::getUserId, userId); // 当前用户ID

        ShopCart info = shopCartService.getOne(queryWrapper);

        if (info != null) {
            // 3. 当前用户已添加过该菜品，更新数量和总价
            Integer newWeight = shopCart.getWeight() + info.getWeight();
            double newTotalPrice = newWeight * info.getPrice();

            LambdaUpdateWrapper<ShopCart> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ShopCart::getName, name)
                    .eq(ShopCart::getUserId, userId) // 仅更新当前用户的记录
                    .set(ShopCart::getWeight, newWeight)
                    .set(ShopCart::getTotalPrice, newTotalPrice);
            res = shopCartService.update(null, updateWrapper);
            log.info("用户{}更新购物车菜品【{}】，数量从{}增加到{}", userId, name, info.getWeight(), newWeight);
        } else {
            // 4. 当前用户未添加过该菜品，新增购物车记录
            shopCart.setUserId(userId); // 绑定当前用户ID
            shopCart.setTotalPrice(shopCart.getPrice() * shopCart.getWeight());
            res = shopCartService.save(shopCart);
            log.info("用户{}新增购物车菜品【{}】，数量：{}", userId, name, shopCart.getWeight());
        }

        if (res) {
            return R.success("加入成功");
        } else {
            return R.fail("加入失败");
        }
    }

    /**
     * 购物车信息获取接口
     */
    @GetMapping("/get")
    public R<List<ShopCart>> getInfo(HttpServletRequest request){
        MyUser currUser = (MyUser) request.getSession().getAttribute("currUser");
        if (currUser == null || currUser.getUserId() == null) {
            throw new CustomException("用户未登录，无法获取购物车信息");
        }
        Long userId = currUser.getUserId();

        LambdaQueryWrapper<ShopCart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ShopCart::getUserId, userId);
        List<ShopCart> list = shopCartService.list(queryWrapper);

        if (list != null) {
            return R.success(list);
        } else {
            return R.fail("获取购物车信息失败");
        }
    }

    /**
     * 购物车删除接口
     */
    @DeleteMapping("/delete/{scId}")
    public R<String> delete(@PathVariable Long scId){
        if (scId == null)  return R.fail("无法获取购物车编号");
        boolean res = shopCartService.removeById(scId);
        if (res) {
            return R.success("删除成功");
        } else {
            return R.fail("删除失败");
        }
    }

}