package com.cityroam.ai;

import com.cityroam.entity.Shop;
import com.cityroam.entity.ShopType;
import com.cityroam.service.IShopService;
import com.cityroam.service.IShopTypeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopContextService {

    private static final int CONTEXT_LIMIT = 8;

    private final IShopService shopService;
    private final IShopTypeService shopTypeService;

    public ShopContextService(IShopService shopService, IShopTypeService shopTypeService) {
        this.shopService = shopService;
        this.shopTypeService = shopTypeService;
    }

    public String retrieve(String query) {
        List<Shop> shops = shopService.list();
        if (shops == null || shops.isEmpty()) {
            return "";
        }
        Map<Long, String> typeNames = typeNames();
        List<String> terms = terms(query);
        List<RankedShop> rankedShops = new ArrayList<>();
        for (Shop shop : shops) {
            rankedShops.add(new RankedShop(shop, typeNames.get(shop.getTypeId()), relevance(shop, typeNames.get(shop.getTypeId()), terms)));
        }
        rankedShops.sort(Comparator.comparingInt(RankedShop::getRelevance).reversed()
                .thenComparing(RankedShop::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedShop::getSold, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RankedShop::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return rankedShops.stream()
                .limit(CONTEXT_LIMIT)
                .map(this::format)
                .collect(Collectors.joining("\n\n"));
    }

    private Map<Long, String> typeNames() {
        List<ShopType> types = shopTypeService.list();
        if (types == null || types.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> names = new HashMap<>();
        for (ShopType type : types) {
            names.put(type.getId(), type.getName());
        }
        return names;
    }

    private List<String> terms(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String[] parts = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                terms.add(part);
            }
        }
        return terms;
    }

    private int relevance(Shop shop, String typeName, List<String> terms) {
        int relevance = 0;
        for (String term : terms) {
            if (contains(shop.getName(), term)) {
                relevance += 3;
            }
            if (contains(shop.getArea(), term)) {
                relevance += 2;
            }
            if (contains(typeName, term)) {
                relevance++;
            }
        }
        return relevance;
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private String format(RankedShop rankedShop) {
        Shop shop = rankedShop.getShop();
        return "名称：" + value(shop.getName())
                + "\n分类：" + value(rankedShop.getTypeName())
                + "\n地址：" + value(shop.getAddress())
                + "\n人均：" + value(shop.getAvgPrice())
                + "\n评分：" + score(shop.getScore());
    }

    private String score(Integer score) {
        return score == null ? "未知" : String.format(Locale.ROOT, "%.1f", score / 10.0D);
    }

    private String value(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }

    private static class RankedShop {
        private final Shop shop;
        private final String typeName;
        private final int relevance;

        private RankedShop(Shop shop, String typeName, int relevance) {
            this.shop = shop;
            this.typeName = typeName;
            this.relevance = relevance;
        }

        private Shop getShop() {
            return shop;
        }

        private String getTypeName() {
            return typeName;
        }

        private int getRelevance() {
            return relevance;
        }

        private Integer getScore() {
            return shop.getScore();
        }

        private Integer getSold() {
            return shop.getSold();
        }

        private Long getId() {
            return shop.getId();
        }
    }
}
