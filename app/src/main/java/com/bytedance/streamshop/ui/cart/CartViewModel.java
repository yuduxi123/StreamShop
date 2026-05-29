package com.bytedance.streamshop.ui.cart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bytedance.streamshop.data.remote.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CartViewModel extends ViewModel {
    private final MutableLiveData<List<Map<String, Object>>> cartItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isEmpty = new MutableLiveData<>(true);
    private final MutableLiveData<Double> totalPrice = new MutableLiveData<>(0.0);
    private final MutableLiveData<Boolean> allSelected = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final ApiService apiService;

    public CartViewModel() {
        this.apiService = new ApiService();
    }

    public LiveData<List<Map<String, Object>>> getCartItems() { return cartItems; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Boolean> getIsEmpty() { return isEmpty; }
    public LiveData<Double> getTotalPrice() { return totalPrice; }
    public LiveData<Boolean> getAllSelected() { return allSelected; }
    public LiveData<String> getError() { return error; }

    public void loadCart() {
        loading.postValue(true);
        new Thread(() -> {
            try {
                List<Map<String, Object>> items = apiService.getCart();
                cartItems.postValue(items);
                isEmpty.postValue(items.isEmpty());
                recalcTotalAndSelectAll(items);
            } catch (Exception e) {
                error.postValue(e.getMessage());
            } finally {
                loading.postValue(false);
            }
        }).start();
    }

    public void toggleItemSelection(int position) {
        List<Map<String, Object>> items = copyItems();
        if (position < 0 || position >= items.size()) return;
        Map<String, Object> item = items.get(position);
        boolean current = (boolean) item.getOrDefault("selected", false);
        item.put("selected", !current);
        cartItems.setValue(items);
        recalcTotalAndSelectAll(items);

        new Thread(() -> {
            try {
                apiService.updateCartItem((String) item.get("id"), Map.of("selected", !current));
            } catch (Exception ignored) {}
        }).start();
    }

    public void toggleSelectAll() {
        List<Map<String, Object>> items = copyItems();
        if (items.isEmpty()) return;
        boolean newVal = !Boolean.TRUE.equals(allSelected.getValue());
        for (Map<String, Object> item : items) {
            item.put("selected", newVal);
        }
        cartItems.setValue(items);
        allSelected.setValue(newVal);
        recalcTotal(items);

        new Thread(() -> {
            for (Map<String, Object> item : items) {
                try {
                    apiService.updateCartItem((String) item.get("id"), Map.of("selected", newVal));
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public void updateQuantity(int position, int delta) {
        List<Map<String, Object>> items = copyItems();
        if (position < 0 || position >= items.size()) return;
        Map<String, Object> item = items.get(position);
        int qty = ((Number) item.get("quantity")).intValue();
        int newQty = Math.max(1, qty + delta);
        item.put("quantity", newQty);
        cartItems.setValue(items);
        recalcTotal(items);

        new Thread(() -> {
            try {
                apiService.updateCartItem((String) item.get("id"), Map.of("quantity", newQty));
            } catch (Exception ignored) {}
        }).start();
    }

    public void deleteItem(int position) {
        List<Map<String, Object>> items = copyItems();
        if (position < 0 || position >= items.size()) return;
        String itemId = (String) items.get(position).get("id");
        items.remove(position);
        cartItems.setValue(items);
        isEmpty.postValue(items.isEmpty());
        recalcTotalAndSelectAll(items);

        new Thread(() -> {
            try { apiService.deleteCartItem(itemId); } catch (Exception ignored) {}
        }).start();
    }

    public void deleteItemById(String itemId) {
        List<Map<String, Object>> items = copyItems();
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).get("id"))) {
                items.remove(i);
                break;
            }
        }
        cartItems.setValue(items);
        isEmpty.postValue(items.isEmpty());
        recalcTotalAndSelectAll(items);

        new Thread(() -> {
            try { apiService.deleteCartItem(itemId); } catch (Exception ignored) {}
        }).start();
    }

    public List<Map<String, Object>> getSelectedItems() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : cartItems.getValue()) {
            if ((boolean) item.getOrDefault("selected", false)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<Map<String, Object>> copyItems() {
        return new ArrayList<>(cartItems.getValue());
    }

    private void recalcTotalAndSelectAll(List<Map<String, Object>> items) {
        recalcTotal(items);
        if (items.isEmpty()) {
            allSelected.postValue(false);
            return;
        }
        for (Map<String, Object> item : items) {
            if (!(boolean) item.getOrDefault("selected", false)) {
                allSelected.postValue(false);
                return;
            }
        }
        allSelected.postValue(true);
    }

    private void recalcTotal(List<Map<String, Object>> items) {
        double total = 0;
        for (Map<String, Object> item : items) {
            if (!(boolean) item.getOrDefault("selected", false)) continue;
            int qty = ((Number) item.get("quantity")).intValue();
            Map<String, Object> product = (Map<String, Object>) item.get("product");
            if (product != null && product.get("price") != null) {
                total += ((Number) product.get("price")).doubleValue() * qty;
            }
        }
        totalPrice.postValue(total);
    }
}
