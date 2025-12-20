//package com.boda.canteen;
//
//import cn.hutool.core.date.DateUtil;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.boda.canteen.common.MyTimeUtils;
//import com.boda.canteen.controller.MyScheduleTask;
//import com.boda.canteen.entity.BlanketOrder;
//import com.boda.canteen.entity.Sale;
//import com.boda.canteen.security.service.BlanketOrderService;
//import com.boda.canteen.security.service.SaleService;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Order;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.TestMethodOrder;
//import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import java.util.List;
//
//@SpringBootTest
//@TestMethodOrder(OrderAnnotation.class) // 强制执行顺序
//public class SaleTaskTest {
//    @Autowired
//    private MyScheduleTask myScheduleTask;
//    @Autowired
//    private SaleService saleService;
//    @Autowired
//    private BlanketOrderService blanketOrderService;
//
//    @Test
//    @Order(1) // 优先执行：生成销售数据
//    public void testAddMonthSale() {
//        // 1. 执行定时任务方法
//        myScheduleTask.addMonthSale();
//
//        // 2. 获取生成的月份（当前系统上个月）
//        String targetMonth = DateUtil.formatDate(DateUtil.lastMonth()).substring(0,7);
//        // 如需强制测试2025-12，打开下面注释：
//        // String targetMonth = "2025-12";
//
//        // 3. 查询生成的数据
//        LambdaQueryWrapper<Sale> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(Sale::getMonth, targetMonth);
//        Sale sale = saleService.getOne(wrapper);
//
//        // 4. 断言验证
//        Assertions.assertNotNull(sale, "[" + targetMonth + "]销售数据未生成");
//        Long expectedTotal = calculateManualTotal(targetMonth);
//        Assertions.assertEquals(expectedTotal, sale.getTotalPrice(), "金额计算错误");
//
//        // 日志输出
//        System.out.println("===== 测试结果 =====");
//        System.out.println("目标月份：" + targetMonth);
//        System.out.println("Sale表存储金额：" + sale.getTotalPrice());
//        System.out.println("手动计算金额：" + expectedTotal);
//        System.out.println("月度销售生成功能验证通过 ✅");
//    }
//
//    // 手动计算：Decimal转Long，和业务代码逻辑一致
//    private Long calculateManualTotal(String month) {
//        String monthFirstDay = month + "-01";
//        List<BlanketOrder> list = blanketOrderService.list(new LambdaQueryWrapper<BlanketOrder>()
//                .between(BlanketOrder::getCreateTime,
//                        MyTimeUtils.getMonthOfBeginTime(monthFirstDay),
//                        MyTimeUtils.getMonthOfEndTime(monthFirstDay)));
//
//        Long total = 0L;
//        for (BlanketOrder bo : list) {
//            if (bo.getTotalPrice() != null) {
//                total += bo.getTotalPrice().longValue(); // Decimal转Long
//            }
//        }
//        return total;
//    }
//
//    @Test
//    @Order(2) // 后执行：验证打印明细
//    public void testPrintData() {
//        String month = "2025-12";
//        String monthFirstDay = month + "-01";
//        List<BlanketOrder> list = blanketOrderService.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BlanketOrder>()
//                .between("createTime",
//                        MyTimeUtils.getMonthOfBeginTime(monthFirstDay),
//                        MyTimeUtils.getMonthOfEndTime(monthFirstDay))
//                .select("name, unit, sum(weight) as weight, sum(totalPrice) as totalPrice")
//                .groupBy("name, unit, price"));
//
//        System.out.println("\n===== [" + month + "]菜品明细 =====");
//        for (BlanketOrder bo : list) {
//            System.out.println(String.format(
//                    "菜品：%s | 单位：%s | 总数量：%s | 总金额：%s",
//                    bo.getName(), bo.getUnit(), bo.getWeight(), bo.getTotalPrice().longValue()
//            ));
//        }
//    }
//}