import { useEffect, useState } from 'react';
import { Plus, Search, Eye, Filter } from 'lucide-react';
import api from '../services/api';

const ORDER_STATUSES = [
  { value: 'pending', label: 'Pending' },
  { value: 'processing', label: 'Processing' },
  { value: 'assigned', label: 'Assigned' },
  { value: 'out_for_delivery', label: 'Out for Delivery' },
  { value: 'delivered', label: 'Delivered' },
  { value: 'commission_locked', label: 'Commission Locked' },
  { value: 'commission_released', label: 'Commission Released' },
  { value: 'paid', label: 'Paid' },
  { value: 'returned', label: 'Returned' },
  { value: 'cancelled', label: 'Cancelled' },
];

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [agents, setAgents] = useState([]);
  const [filters, setFilters] = useState({
    search: '',
    status: '',
    date_from: '',
    date_to: '',
    delivery_zone: '',
  });
  const [showModal, setShowModal] = useState(false);
  const [showTimeline, setShowTimeline] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [zones, setZones] = useState([]);
  const [formData, setFormData] = useState({
    customer_name: '',
    customer_phone: '',
    customer_email: '',
    customer_address: '',
    delivery_zone: '',
    agent_code_used: '',
    product_sku: '',
    product_name: '',
    total_value: '',
  });

  useEffect(() => {
    fetchOrders();
    fetchAgents();
    fetchZones();
  }, []);

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const response = await api.get('/orders', { params: filters });
      setOrders(response.data);
    } catch (err) {
      console.error(err);
    }
    setLoading(false);
  };

  const fetchAgents = async () => {
    try {
      const response = await api.get('/agents');
      setAgents(response.data);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchZones = async () => {
    try {
      const response = await api.get('/orders/zones');
      setZones(response.data);
    } catch (err) {
      console.error(err);
    }
  };

  const handleFilterChange = (key, value) => {
    setFilters({ ...filters, [key]: value });
  };

  const applyFilters = () => {
    fetchOrders();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await api.post('/orders', formData);
      setShowModal(false);
      resetForm();
      fetchOrders();
    } catch (err) {
      alert(err.response?.data?.message || 'Error creating order');
    }
  };

  const viewTimeline = async (order) => {
    try {
      const response = await api.get(`/orders/${order.id}/timeline`);
      setTimeline(response.data.timeline);
      setSelectedOrder(order);
      setShowTimeline(true);
    } catch (err) {
      alert('Error loading timeline');
    }
  };

  const getStatusColor = (status) => {
    const colors = {
      pending: 'bg-gray-100 text-gray-700',
      processing: 'bg-blue-100 text-blue-700',
      assigned: 'bg-indigo-100 text-indigo-700',
      out_for_delivery: 'bg-yellow-100 text-yellow-700',
      delivered: 'bg-green-100 text-green-700',
      commission_locked: 'bg-orange-100 text-orange-700',
      commission_released: 'bg-teal-100 text-teal-700',
      paid: 'bg-green-100 text-green-700',
      returned: 'bg-red-100 text-red-700',
      cancelled: 'bg-red-100 text-red-700',
    };
    return colors[status] || 'bg-gray-100 text-gray-700';
  };

  const resetForm = () => {
    setFormData({
      customer_name: '',
      customer_phone: '',
      customer_email: '',
      customer_address: '',
      delivery_zone: '',
      agent_code_used: '',
      product_sku: '',
      product_name: '',
      total_value: '',
    });
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-semibold text-[#201515]">Orders</h2>
          <p className="text-sm text-[#939084] mt-1">Manage orders & attribution</p>
        </div>
        <button
          onClick={() => {
            resetForm();
            setShowModal(true);
          }}
          className="flex items-center gap-2 px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600] transition-colors"
        >
          <Plus size={18} />
          Create Order
        </button>
      </div>

      <div className="bg-white border border-[#c5c0b1] rounded-lg mb-6">
        <div className="p-4 border-b border-[#c5c0b1]">
          <div className="flex items-center gap-4 flex-wrap">
            <div className="relative flex-1 min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#939084]" size={18} />
              <input
                type="text"
                placeholder="Search order number, customer..."
                value={filters.search}
                onChange={(e) => handleFilterChange('search', e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && applyFilters()}
                className="w-full pl-10 pr-4 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
              />
            </div>
            <select
              value={filters.status}
              onChange={(e) => handleFilterChange('status', e.target.value)}
              className="px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
            >
              <option value="">All Statuses</option>
              {ORDER_STATUSES.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
            <input
              type="date"
              value={filters.date_from}
              onChange={(e) => handleFilterChange('date_from', e.target.value)}
              className="px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
              placeholder="From Date"
            />
            <input
              type="date"
              value={filters.date_to}
              onChange={(e) => handleFilterChange('date_to', e.target.value)}
              className="px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
              placeholder="To Date"
            />
            <button
              onClick={applyFilters}
              className="px-4 py-2 bg-[#36342e] text-white rounded-md text-sm font-medium hover:bg-[#201515]"
            >
              <Filter size={18} />
            </button>
          </div>
        </div>

        <table className="w-full">
          <thead className="bg-[#eceae3]">
            <tr>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Order #</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Customer</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Agent</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Value</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Status</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Date</th>
              <th className="text-right px-4 py-3 text-sm font-semibold text-[#36342e]">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-[#939084]">
                  Loading...
                </td>
              </tr>
            ) : orders.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-[#939084]">
                  No orders found
                </td>
              </tr>
            ) : (
              orders.map((order) => (
                <tr key={order.id} className="border-t border-[#c5c0b1]">
                  <td className="px-4 py-3">
                    <span className="font-mono text-sm text-[#201515]">{order.order_number}</span>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-sm font-medium text-[#201515]">{order.customer_name}</p>
                    <p className="text-xs text-[#939084]">{order.customer_phone}</p>
                  </td>
                  <td className="px-4 py-3">
                    {order.agent_code_used ? (
                      <span className="text-sm font-mono bg-[#eceae3] px-2 py-1 rounded">
                        {order.agent_code_used}
                      </span>
                    ) : (
                      <span className="text-sm text-[#939084]">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-sm font-medium text-[#201515]">
                      Rs. {parseFloat(order.total_value).toLocaleString()}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(order.status)}`}>
                      {ORDER_STATUSES.find(s => s.value === order.status)?.label || order.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">
                    {new Date(order.created_at).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => viewTimeline(order)}
                      className="inline-flex p-2 text-[#36342e] hover:text-[#ff4f00] transition-colors"
                      title="View Timeline"
                    >
                      <Eye size={16} />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg w-full max-w-lg mx-4 border border-[#c5c0b1] max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-[#c5c0b1] sticky top-0 bg-white">
              <h3 className="text-lg font-semibold text-[#201515]">Create Order</h3>
              <p className="text-sm text-[#939084]">Manual order entry for walk-in/showroom customers</p>
            </div>
            <form onSubmit={handleSubmit} className="p-6">
              <div className="space-y-4">
                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Customer Details</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Customer Name *</label>
                      <input
                        type="text"
                        value={formData.customer_name}
                        onChange={(e) => setFormData({ ...formData, customer_name: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Phone *</label>
                      <input
                        type="tel"
                        value={formData.customer_phone}
                        onChange={(e) => setFormData({ ...formData, customer_phone: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Email</label>
                      <input
                        type="email"
                        value={formData.customer_email}
                        onChange={(e) => setFormData({ ...formData, customer_email: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Delivery Zone</label>
                      <input
                        type="text"
                        value={formData.delivery_zone}
                        onChange={(e) => setFormData({ ...formData, delivery_zone: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        placeholder="e.g., Colombo 01"
                      />
                    </div>
                    <div className="md:col-span-2">
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Address *</label>
                      <input
                        type="text"
                        value={formData.customer_address}
                        onChange={(e) => setFormData({ ...formData, customer_address: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Product Details</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Product SKU</label>
                      <input
                        type="text"
                        value={formData.product_sku}
                        onChange={(e) => setFormData({ ...formData, product_sku: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Product Name</label>
                      <input
                        type="text"
                        value={formData.product_name}
                        onChange={(e) => setFormData({ ...formData, product_name: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Total Value *</label>
                      <input
                        type="number"
                        value={formData.total_value}
                        onChange={(e) => setFormData({ ...formData, total_value: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Attribution</h4>
                  <div>
                    <label className="block text-sm font-medium text-[#36342e] mb-1">Referral Agent</label>
                    <select
                      value={formData.agent_code_used}
                      onChange={(e) => setFormData({ ...formData, agent_code_used: e.target.value })}
                      className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                    >
                      <option value="">— No Agent —</option>
                      {agents.map((agent) => (
                        <option key={agent.id} value={agent.code}>
                          {agent.name} ({agent.code})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-[#c5c0b1]">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 border border-[#c5c0b1] rounded-md text-sm font-medium text-[#36342e] hover:bg-[#eceae3]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600]"
                >
                  Create Order
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showTimeline && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg w-full max-w-md mx-4 border border-[#c5c0b1]">
            <div className="px-6 py-4 border-b border-[#c5c0b1]">
              <h3 className="text-lg font-semibold text-[#201515]">Order Timeline</h3>
              <p className="text-sm text-[#939084]">{selectedOrder?.order_number}</p>
            </div>
            <div className="p-6 max-h-[60vh] overflow-y-auto">
              <div className="relative">
                <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-[#c5c0b1]"></div>
                <div className="space-y-6">
                  {timeline.map((item, idx) => (
                    <div key={idx} className="relative pl-10">
                      <div className="absolute left-2.5 w-3 h-3 rounded-full bg-[#ff4f00] border-2 border-white"></div>
                      <div>
                        <p className="text-sm font-medium text-[#201515] capitalize">{item.status.replace(/_/g, ' ')}</p>
                        <p className="text-xs text-[#939084]">
                          {item.timestamp ? new Date(item.timestamp).toLocaleString() : '—'}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="px-6 py-4 border-t border-[#c5c0b1]">
              <button
                onClick={() => setShowTimeline(false)}
                className="w-full px-4 py-2 bg-[#36342e] text-white rounded-md text-sm font-medium hover:bg-[#201515]"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}