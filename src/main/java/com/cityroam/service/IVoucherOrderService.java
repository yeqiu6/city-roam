package com.cityroam.service;

import com.cityroam.dto.Result;
import com.cityroam.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long id);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
