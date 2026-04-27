import { create } from 'zustand';
import { agentService } from '../services/api';

export const useAgentStore = create((set, get) => ({
  agents: [],
  loading: false,
  error: null,
  pagination: null,
  
  fetchAgents: async (params) => {
    set({ loading: true, error: null });
    try {
      const response = await agentService.getAll(params);
      const data = response.data.data || response.data;
      const pagination = response.data;
      set({ agents: data, pagination, loading: false });
    } catch (error) {
      set({ error: error.message, loading: false });
    }
  },
  
  addAgent: async (agent) => {
    set({ loading: true, error: null });
    try {
      const response = await agentService.create(agent);
      set({ agents: [response.data, ...get().agents], loading: false });
      return response.data;
    } catch (error) {
      set({ loading: false });
      throw error;
    }
  },
  
  updateAgent: async (id, agent) => {
    try {
      const response = await agentService.update(id, agent);
      set({
        agents: get().agents.map((a) => (a.id === id ? response.data : a)),
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
  
  deleteAgent: async (id) => {
    try {
      await agentService.delete(id);
      set({ agents: get().agents.filter((a) => a.id !== id) });
    } catch (error) {
      throw error;
    }
  },
  
  toggleAgentStatus: async (id) => {
    try {
      const response = await agentService.toggleStatus(id);
      set({
        agents: get().agents.map((a) => (a.id === id ? response.data : a)),
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  },
}));