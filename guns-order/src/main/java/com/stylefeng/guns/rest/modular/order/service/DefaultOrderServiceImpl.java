package com.stylefeng.guns.rest.modular.order.service;
import	java.math.BigDecimal;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.stylefeng.guns.api.cinema.CinemaServiceAPI;
import com.stylefeng.guns.api.cinema.vo.FilmInfoVO;
import com.stylefeng.guns.api.cinema.vo.OrderQueryVO;
import com.stylefeng.guns.api.order.OrderServiceAPI;
import com.stylefeng.guns.api.order.vo.OrderVO;
import com.stylefeng.guns.core.util.UUIDUtil;
import com.stylefeng.guns.rest.common.persistence.dao.MoocOrderTMapper;
import com.stylefeng.guns.rest.common.persistence.model.MoocOrderT;
import com.stylefeng.guns.rest.common.util.FTPUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Service(interfaceClass = OrderServiceAPI.class, group = "default")
public class DefaultOrderServiceImpl implements OrderServiceAPI {

    @Autowired
    private MoocOrderTMapper moocOrderTMapper;
    @Reference(interfaceClass = CinemaServiceAPI.class, check = false)
    private CinemaServiceAPI cinemaServiceAPI;
    @Autowired
    private FTPUtil ftpUtil;

    @Override
    public boolean isTrueSeats(String fieldId, String seats) {
        //根据FieldId找到对应的座位位置图
        String seatPath = moocOrderTMapper.getSeatsByFieldId(fieldId);
        //获取位置图,判断seats是否为真
        String fileStrByAddress = ftpUtil.getFileStrByAddress(seatPath);

        //将fileStrByAddress转换为json对象
        JSONObject jsonObject = JSONObject.parseObject(fileStrByAddress);
        //seats=1,2,3
        String ids = jsonObject.get("ids").toString();

        String[] idArrs = ids.split(",");
        String[] seatArrs = seats.split(",");
        int isTrue = 0;
        //每次匹配上,都给isTrue加1
        for (String idArr : idArrs) {
            for (String seatArr : seatArrs) {
                if(seatArr.equalsIgnoreCase(idArr)){
                    isTrue ++;
                }
            }
        }
        return isTrue == seatArrs.length;
    }

    @Override
    public boolean isNotSoldSeats(String fieldId, String seats) {
        EntityWrapper<MoocOrderT> entityWrapper = new EntityWrapper<>();
        entityWrapper.eq("field_id", fieldId);
        List<MoocOrderT> moocOrderTS = moocOrderTMapper.selectList(entityWrapper);
        String[] seatArrs = seats.split(",");
        for (MoocOrderT moocOrderT : moocOrderTS) {
            String[] ids = moocOrderT.getSeatsIds().split(",");
            for (String id : ids) {
                for (String seatArr : seatArrs) {
                    if(id.equalsIgnoreCase(seatArr)){
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public OrderVO saveOrderInfo(Integer fieldId, String soldSeats, String seatsName, Integer userId) {
        //编号
        String uuid = UUIDUtil.getUuid();

        //影片信息
        FilmInfoVO filmInfoByFieldId = cinemaServiceAPI.getFilmInfoByFieldId(fieldId);
        int filmId = Integer.parseInt(filmInfoByFieldId.getFilmId());
        //获取影院信息
        OrderQueryVO orderQueryVO = cinemaServiceAPI.getOrderNeeds(fieldId);
        int cinemaId = Integer.parseInt(orderQueryVO.getCinemaId());
        double filmPrice = Double.parseDouble(orderQueryVO.getFilmPrice());

        //求订单总金额
        int solds = soldSeats.split(",").length;
        double totalPrice = getTotalPrice(solds, filmPrice);
        MoocOrderT moocOrderT = new MoocOrderT();
        moocOrderT.setUuid(uuid);
        moocOrderT.setSeatsName(seatsName);
        moocOrderT.setSeatsIds(soldSeats);
        moocOrderT.setOrderUser(userId);
        moocOrderT.setOrderPrice(totalPrice);
        moocOrderT.setFilmPrice(filmPrice);
        moocOrderT.setFilmId(filmId);
        moocOrderT.setFieldId(fieldId);
        moocOrderT.setCinemaId(cinemaId);
        Integer insert = moocOrderTMapper.insert(moocOrderT);
        if(insert > 0){
            //返回查询结果
            OrderVO orderVO = moocOrderTMapper.getOrderInfoById(uuid);
            if(null == orderVO || null == orderVO.getOrderId()){
                log.error("订单信息查询失败,订单编号为{}", uuid);
                return null;
            }
            return orderVO;
        }
        //插入出错
        log.error("订单插入失败");
        return null;
    }

    private double getTotalPrice(int solds ,double filmPrice){
        BigDecimal soldDeci = new BigDecimal(solds);
        BigDecimal filmPriceDeci = new BigDecimal(filmPrice);
        BigDecimal result = soldDeci.multiply(filmPriceDeci);

        //四舍五入,取小数点后两位
        BigDecimal bigDecimal = result.setScale(2, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }

    @Override
    public Page<OrderVO> getOrderByUserId(Integer userId, Page<OrderVO> page) {
        Page<OrderVO> result = new Page<>();
        if(null == userId){
            log.error("订单查询业务失败,用户编号为传入");
            return null;
        }else {
            List<OrderVO> orderInfoByUserId = moocOrderTMapper.getOrdersByUserId(userId, page);
            if(null == orderInfoByUserId && orderInfoByUserId.size() == 0){
                result.setTotal(0);
                result.setRecords(new ArrayList<>());
                return result;
            }
            //获取订单总数
            EntityWrapper<MoocOrderT> entityWrapper = new EntityWrapper<>();
            entityWrapper.eq("order_user", userId);
            Integer count = moocOrderTMapper.selectCount(entityWrapper);
            //将结果放入Page中
            result.setTotal(count);
            result.setRecords(orderInfoByUserId);
            return result;
        }
    }

    @Override
    public String getSoldSeatsByField(Integer fieldId) {
        if(null == fieldId){
            log.error("查询已售座位错误,未传入任何场次编号");
            return "";
        }
        return moocOrderTMapper.getSoldSeatsByField(fieldId);
    }

    @Override
    public OrderVO getOrderInfoById(String orderId) {
        return moocOrderTMapper.getOrderInfoById(orderId);
    }

    @Override
    public boolean paySuccess(String orderId) {
        MoocOrderT moocOrderT = new MoocOrderT();
        moocOrderT.setUuid(orderId);
        moocOrderT.setOrderStatus(1);
        Integer integer = moocOrderTMapper.updateById(moocOrderT);
        return integer >= 1;
    }

    @Override
    public boolean payFail(String orderId) {
        MoocOrderT moocOrderT = new MoocOrderT();
        moocOrderT.setUuid(orderId);
        moocOrderT.setOrderStatus(2);
        Integer integer = moocOrderTMapper.updateById(moocOrderT);
        return integer >= 1;
    }
}
