package com.cityroam.ai;

import com.cityroam.entity.Shop;
import com.cityroam.entity.ShopType;
import com.cityroam.service.IShopService;
import com.cityroam.service.IShopTypeService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopContextServiceTest {

    @Test
    void retrieveLimitsResultsAndFormatsOnlyTrustedShopFields() {
        IShopService shopService = mock(IShopService.class);
        IShopTypeService shopTypeService = mock(IShopTypeService.class);
        List<Shop> shops = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            shops.add(new Shop()
                    .setId((long) index)
                    .setName("西湖咖啡" + index)
                    .setTypeId(1L)
                    .setArea("西湖")
                    .setAddress("地址" + index)
                    .setAvgPrice(50L)
                    .setScore(45)
                    .setSold(index));
        }
        when(shopService.list()).thenReturn(shops);
        when(shopTypeService.list()).thenReturn(java.util.Collections.singletonList(
                new ShopType().setId(1L).setName("咖啡")));

        String context = new ShopContextService(shopService, shopTypeService).retrieve("西湖 咖啡");

        assertThat(context).contains("名称：西湖咖啡")
                .contains("分类：咖啡")
                .contains("地址：地址")
                .contains("人均：50")
                .contains("评分：4.5")
                .doesNotContain("图片", "经度", "营业时间");
        assertThat(context.split("名称：", -1).length - 1).isLessThanOrEqualTo(8);
    }

    @Test
    void retrieveExcludesZeroRelevanceShopsAndRepresentsNoMatch() {
        IShopService shopService = mock(IShopService.class);
        IShopTypeService shopTypeService = mock(IShopTypeService.class);
        when(shopService.list()).thenReturn(java.util.Collections.singletonList(new Shop()
                .setId(1L).setName("西湖咖啡").setTypeId(1L).setArea("西湖")));
        when(shopTypeService.list()).thenReturn(java.util.Collections.singletonList(
                new ShopType().setId(1L).setName("咖啡")));

        String context = new ShopContextService(shopService, shopTypeService).retrieve("火锅");

        assertThat(context).isEqualTo(ShopContextService.NO_MATCH_CONTEXT);
        assertThat(context).doesNotContain("西湖咖啡");
    }

    @Test
    void retrieveByIdFormatsOnlyTheExactSelectedShop() {
        IShopService shopService = mock(IShopService.class);
        IShopTypeService shopTypeService = mock(IShopTypeService.class);
        Shop selected = new Shop().setId(7L).setName("目标店铺").setTypeId(1L)
                .setAddress("目标地址").setAvgPrice(50L).setScore(45);
        when(shopService.getById(7L)).thenReturn(selected);
        when(shopTypeService.list()).thenReturn(java.util.Collections.singletonList(
                new ShopType().setId(1L).setName("咖啡")));

        String context = new ShopContextService(shopService, shopTypeService).retrieveById(7L);

        assertThat(context).contains("名称：目标店铺", "分类：咖啡", "地址：目标地址", "人均：50", "评分：4.5");
        verify(shopService).getById(7L);
    }
}
