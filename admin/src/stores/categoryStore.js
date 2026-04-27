import { create } from 'zustand';
import { categoryService } from '../services/api';

export const useCategoryStore = create((set, get) => ({
  categories: [],
  loading: false,
  error: null,
  
  fetchCategories: async () => {
    set({ loading: true, error: null });
    try {
      const response = await categoryService.getAll();
      set({ categories: response.data, loading: false });
    } catch (error) {
      set({ error: error.message, loading: false });
    }
  },
  
  addCategory: async (category) => {
    try {
      const response = await categoryService.create(category);
      set({ categories: [...get().categories, response.data] });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  updateCategory: async (id, category) => {
    try {
      const response = await categoryService.update(id, category);
      set({
        categories: get().categories.map((c) => (c.id === id ? response.data : c)),
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  deleteCategory: async (id) => {
    try {
      await categoryService.delete(id);
      set({ categories: get().categories.filter((c) => c.id !== id) });
    } catch (error) {
      throw error;
    }
  },
}));