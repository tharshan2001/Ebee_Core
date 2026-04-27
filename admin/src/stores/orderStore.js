import { create } from 'zustand';
import { orderService } from '../services/api';

export const useOrderStore = create((set, get) => ({
  orders: [],
  loading: false,
  error: null,
  stats: null,
  
  fetchOrders: async (params) => {
    set({ loading: true, error: null });
    try {
      const response = await orderService.getAll(params);
      set({ orders: response.data, loading: false });
    } catch (error) {
      set({ error: error.message, loading: false });
    }
  },
  
  addOrder: async (order) => {
    set({ loading: true, error: null });
    try {
      const response = await orderService.create(order);
      set({ orders: [response.data, ...get().orders], loading: false });
      return response.data;
    } catch (error) {
      set({ loading: false });
      throw error;
    }
  },
  
  updateOrder: async (id, order) => {
    try {
      const response = await orderService.update(id, order);
      set({
        orders: get().orders.map((o) => (o.id === id ? response.data : o)),
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  deleteOrder: async (id) => {
    try {
      await orderService.delete(id);
      set({ orders: get().orders.filter((o) => o.id !== id) });
    } catch (error) {
      throw error;
    }
  },
  
  getTimeline: async (id) => {
    try {
      const response = await orderService.getTimeline(id);
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  assignDriver: async (id, driverId) => {
    try {
      const response = await orderService.assignDriver(id, driverId);
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  resolveAttribution: async (id, data) => {
    try {
      const response = await orderService.resolveAttribution(id, data);
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  fetchStats: async () => {
    try {
      const response = await orderService.stats();
      set({ stats: response.data });
    } catch (error) {
      console.error(error);
    }
  },
  
  fetchZones: async () => {
    try {
      const response = await orderService.zones();
      return response.data;
    } catch (error) {
      console.error(error);
    }
  },
}));