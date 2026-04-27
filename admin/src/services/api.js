import axios from 'axios';

const API_URL = 'http://localhost:8081/api';

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const productService = {
  getAll: (params) => api.get('/products', { params }),
  getById: (id) => api.get(`/products/${id}`),
  create: (data) => api.post('/products', data),
  update: (id, data) => api.put(`/products/${id}`, data),
  delete: (id) => api.delete(`/products/${id}`),
  archive: (id, archived) => api.patch(`/products/${id}/archive`, { archived }),
  search: (params) => api.get('/products/search', { params }),
  stats: () => api.get('/products/stats'),
};

export const dashboardService = {
  stats: () => api.get('/dashboard/stats'),
  recentProducts: () => api.get('/dashboard/recent-products'),
};

export const categoryService = {
  getAll: () => api.get('/categories'),
  getById: (id) => api.get(`/categories/${id}`),
  create: (data) => api.post('/categories', data),
  update: (id, data) => api.put(`/categories/${id}`, data),
  delete: (id) => api.delete(`/categories/${id}`),
};

export const agentService = {
  getAll: (params) => api.get('/agents', { params }),
  getById: (id) => api.get(`/agents/${id}`),
  create: (data) => api.post('/agents', data),
  update: (id, data) => api.put(`/agents/${id}`, data),
  delete: (id) => api.delete(`/agents/${id}`),
  toggleStatus: (id) => api.patch(`/agents/${id}/toggle-status`),
  stats: () => api.get('/agents/stats'),
};

export const orderService = {
  getAll: (params) => api.get('/orders', { params }),
  getById: (id) => api.get(`/orders/${id}`),
  create: (data) => api.post('/orders', data),
  update: (id, data) => api.put(`/orders/${id}`, data),
  delete: (id) => api.delete(`/orders/${id}`),
  getTimeline: (id) => api.get(`/orders/${id}/timeline`),
  assignDriver: (id, driverId) => api.post(`/orders/${id}/assign-driver`, { driver_id: driverId }),
  resolveAttribution: (id, data) => api.post(`/orders/${id}/resolve-attribution`, data),
  stats: () => api.get('/orders/stats'),
  zones: () => api.get('/orders/zones'),
};

export const driverService = {
  getAll: (params) => api.get('/drivers', { params }),
  getById: (id) => api.get(`/drivers/${id}`),
  create: (data) => api.post('/drivers', data),
  update: (id, data) => api.put(`/drivers/${id}`, data),
  delete: (id) => api.delete(`/drivers/${id}`),
  toggleStatus: (id) => api.patch(`/drivers/${id}/toggle-status`),
  updateLocation: (id, lat, lng) => api.patch(`/drivers/${id}/location`, { lat, lng }),
};

export const leadService = {
  getAll: (params) => api.get('/leads', { params }),
  getById: (id) => api.get(`/leads/${id}`),
  update: (id, data) => api.put(`/leads/${id}`, data),
  convert: (id, orderId) => api.post(`/leads/${id}/convert`, { order_id: orderId }),
  stats: () => api.get('/leads/stats'),
};

export const configService = {
  getAll: () => api.get('/configs'),
  get: (key) => api.get(`/configs/${key}`),
  update: (data) => api.put('/configs', data),
  bulkUpdate: (configs) => api.patch('/configs/bulk', { configs }),
  seed: () => api.post('/configs/seed'),
};

export default api;