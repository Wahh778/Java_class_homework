package com.boda.canteen;

import com.boda.canteen.controller.MyScheduleTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryMenuTest {
    @Autowired
    private MyScheduleTask myScheduleTask;

    @Test
    public void testAddHistoryMenu() {
        // 手动调用历史菜单生成方法
        myScheduleTask.addHistoryMenu();
        System.out.println("历史菜单生成方法执行完成");
    }
}