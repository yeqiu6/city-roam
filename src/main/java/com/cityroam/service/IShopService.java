package com.cityroam.service;

import com.cityroam.dto.Result;
import com.cityroam.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id) throws InterruptedException;

    Result updateShop(Shop shop);
}
