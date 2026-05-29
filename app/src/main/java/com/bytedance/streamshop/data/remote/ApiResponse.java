package com.bytedance.streamshop.data.remote;

import java.util.List;

public class ApiResponse<T> {
    private List<T> data;
    private int total;
    private int page;
    private int limit;

    public List<T> getData() { return data; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }
}
