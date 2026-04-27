import { create } from 'zustand';
import { productService } from '../services/api';

export const useProductStore = create((set, get) => ({
  products: [],
  loading: false,
  error: null,
  pagination: null,
  
  fetchProducts: async (params) => {
    set({ loading: true, error: null });
    try {
      const response = await productService.getAll(params);
      const data = response.data.data || response.data;
      const pagination = response.data;
      set({ products: data, pagination, loading: false });
    } catch (error) {
      set({ error: error.message, loading: false });
    }
  },
  
  addProduct: async (product) => {
    try {
      const response = await productService.create(product);
      set({ products: [response.data, ...get().products] });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  updateProduct: async (id, product) => {
    try {
      const response = await productService.update(id, product);
      set({
        products: get().products.map((p) => (p.id === id ? response.data : p)),
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  deleteProduct: async (id) => {
    try {
      await productService.delete(id);
      set({ products: get().products.filter((p) => p.id !== id) });
    } catch (error) {
      throw error;
    }
  },
}));