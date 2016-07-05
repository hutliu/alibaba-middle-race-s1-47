package com.alibaba.middleware.race.rocketmq;

import com.alibaba.middleware.race.RaceConfig;
import com.alibaba.middleware.race.RaceUtils;
import com.alibaba.middleware.race.model.OrderMessage;
import com.alibaba.middleware.race.model.PaymentMessage;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.SendCallback;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.SimpleFormatter;


public class CreateData {

    private static Random rand = new Random();
    private static AtomicInteger atomIntTb = new AtomicInteger(0);
    private static AtomicInteger atomIntTm = new AtomicInteger(0);
    private static AtomicInteger atomIntPy = new AtomicInteger(0);
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");




    public static void main(String[] args) throws MQClientException, InterruptedException, IOException {
        BufferedWriter tb_bw_data = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\tb_data.txt")));
        BufferedWriter tb_bw_result = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\tb_result.txt")));
        BufferedWriter tm_bw_data = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\tm_data.txt")));
        BufferedWriter tm_bw_result = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\tm_result.txt")));
        BufferedWriter py_bw_data = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\py_data.txt")));
        BufferedWriter py_bw_result = new BufferedWriter(new FileWriter(new File("E:\\mdw_data\\py_result.txt")));


        HashMap<Long, Double> tbMap = new HashMap<>();
        HashMap<Long, Double> tmMap = new HashMap<>();
        HashMap<Long, double[]> pyMap = new HashMap<>();

        final String [] topics = new String[]{RaceConfig.MqTaobaoTradeTopic, RaceConfig.MqTmallTradeTopic};
        String str = "";
        long starttime = System.currentTimeMillis();
        int count = 0;
        while (count < 20000){
            for (int i = 0; i < 100; i++) {
                try {
                    final int platform = rand.nextInt(2);
                    if (platform == 0) {
                        atomIntTb.addAndGet(1);
                    } else {
                        atomIntTm.addAndGet(1);
                    }
                    final OrderMessage orderMessage = ( platform == 0 ? OrderMessage.createTbaoMessage() : OrderMessage.createTmallMessage());
                    orderMessage.setCreateTime(System.currentTimeMillis());
                    PaymentMessage[] paymentMessages = PaymentMessage.createPayMentMsg(orderMessage);

                    long timeStamp = sdf.parse(sdf.format(new Date(orderMessage.getCreateTime()))).getTime()/1000;
                    if (platform == 0) {
                        Double totalPrice = tbMap.get(timeStamp);
                        tb_bw_data.write(orderMessage.toString().split("([{|}])")[1] + "\n");
                        if (totalPrice == null) {
                            tbMap.put(timeStamp,orderMessage.getTotalPrice());
                        } else {
                            totalPrice += orderMessage.getTotalPrice();
                            tbMap.put(timeStamp, totalPrice);
                        }
                    } else {
                        tm_bw_data.write(orderMessage.toString().split("([{|}])")[1] + "\n");
                        Double totalPrice = tmMap.get(timeStamp);
                        if (totalPrice == null) {
                            tmMap.put(timeStamp,orderMessage.getTotalPrice());
                        } else {
                            totalPrice += orderMessage.getTotalPrice();
                            tmMap.put(timeStamp, totalPrice);
                        }
                    }

                    double amount = 0;
                    for (final PaymentMessage paymentMessage : paymentMessages) {
                        timeStamp = sdf.parse(sdf.format(new Date(paymentMessage.getCreateTime()))).getTime()/1000;
                        double[] amounts = pyMap.get(timeStamp);
                        if (amounts == null) {
                            amounts = new double[]{0,0};
                        }

                        amounts[paymentMessage.getPayPlatform()] += paymentMessage.getPayAmount();
                        pyMap.put(timeStamp,amounts);
                        atomIntPy.addAndGet(1);
                        py_bw_data.write(paymentMessage.toString().split("([{|}])")[1] + "\n");
                        int retVal = Double.compare(paymentMessage.getPayAmount(), 0);
                        if (retVal < 0) {
                            throw new RuntimeException("price < 0 !!!!!!!!");
                        }

                        if (retVal > 0) {
                            amount += paymentMessage.getPayAmount();
                        }
                    }
                    if (Double.compare(amount, orderMessage.getTotalPrice()) != 0) {
                        throw new RuntimeException("totalprice is not equal.");
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            tb_bw_data.flush();
            tm_bw_data.flush();
            py_bw_data.flush();
            Thread.sleep(945);
            System.out.println(count);
            count += 100;
        }

        Set<Map.Entry<Long, Double>> tbEntrySet = tbMap.entrySet();
        Set<Map.Entry<Long, Double>> tmEntrySet = tmMap.entrySet();
        Set<Map.Entry<Long, double[]>> pyEntrySet = pyMap.entrySet();

        for (Map.Entry entry : tbEntrySet) {
            tb_bw_result.write(entry.getKey()+","+entry.getValue()+"\n");
        }
        tb_bw_result.write("0,0");

        for (Map.Entry entry : tmEntrySet) {
            tm_bw_result.write(entry.getKey()+","+entry.getValue()+"\n");
        }
        tm_bw_result.write("0,0");

        for (Map.Entry<Long,double[]> entry : pyEntrySet) {
            py_bw_result.write(entry.getKey()+","+entry.getValue()[0]+","+entry.getValue()[1]+","+entry.getValue()[1]/entry.getValue()[0]+"\n");
        }
        py_bw_result.write("0,0,0");
        System.out.println(System.currentTimeMillis() - starttime);

        System.out.println("TB: "+atomIntTb);
        System.out.println("TM: "+atomIntTm);
        System.out.println("PY: "+atomIntPy);

        tb_bw_data.flush();
        tm_bw_data.flush();
        py_bw_data.flush();
        tb_bw_result.flush();
        tm_bw_result.flush();
        py_bw_result.flush();

        tb_bw_data.close();
        tm_bw_data.close();
        py_bw_data.close();
        tb_bw_result.close();
        tm_bw_result.close();
        py_bw_result.close();
    }
}
