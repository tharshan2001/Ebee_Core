import { Package, FolderTree, TrendingUp, Users, ShoppingCart, DollarSign, AlertTriangle } from 'lucide-react';
import { useProductStore } from '../stores/productStore';
import { useCategoryStore } from '../stores/categoryStore';
import { useEffect, useState } from 'react';
import api from '../services/api';

export default function Dashboard() {
  const { products, fetchProducts } = useProductStore();
  const { categories, fetchCategories } = useCategoryStore();
  const [stats, setStats] = useState({
    agents: { total: 0, active: 0 },
    orders: { total: 0, pending: 0, delivered: 0, totalValue: 0 },
    drivers: { total: 0, online: 0 },
    commissions: { locked: 0, payable: 0 }
  });

  useEffect(() => {
    fetchProducts();
    fetchCategories();
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const [agentsRes, ordersRes, driversRes] = await Promise.all([
        api.get('/agents/stats'),
        api.get('/orders/stats'),
        api.get('/drivers')
      ]);

      const driversData = driversRes.data;
      const onlineDrivers = driversData.filter(d => d.status === 'online' || d.status === 'on_duty').length;

      setStats({
        agents: { total: agentsRes.data.total, active: agentsRes.data.active },
        orders: { 
          total: ordersRes.data.total, 
          pending: ordersRes.data.pending, 
          delivered: ordersRes.data.delivered || 0,
          totalValue: ordersRes.data.total_value || 0
        },
        drivers: { total: driversData.length, online: onlineDrivers },
        commissions: { locked: ordersRes.data.commission_locked || 0, payable: ordersRes.data.commission_released || 0 }
      });
    } catch (err) {
      console.error(err);
    }
  };

  const activeProducts = products.filter((p) => !p.archived).length;

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-2xl font-semibold text-[#201515]">Dashboard</h2>
        <p className="text-sm text-[#939084] mt-1">Overview of your business</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <Users className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Agents</p>
              <p className="text-xl font-semibold text-[#201515]">{stats.agents.total}</p>
              <p className="text-xs text-green-600">{stats.agents.active} active</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <ShoppingCart className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Orders</p>
              <p className="text-xl font-semibold text-[#201515]">{stats.orders.total}</p>
              <p className="text-xs text-[#ff4f00]">{stats.orders.pending} pending</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <Package className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Products</p>
              <p className="text-xl font-semibold text-[#201515]">{products.length}</p>
              <p className="text-xs text-green-600">{activeProducts} active</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <TrendingUp className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Sales Value</p>
              <p className="text-xl font-semibold text-[#201515]">Rs.{parseInt(stats.orders.totalValue || 0).toLocaleString()}</p>
              <p className="text-xs text-green-600">{stats.orders.delivered} delivered</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-orange-100 rounded-lg">
              <DollarSign className="text-orange-600" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Commission Locked</p>
              <p className="text-xl font-semibold text-[#201515]">{stats.commissions.locked}</p>
              <p className="text-xs text-orange-600">awaiting release</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-green-100 rounded-lg">
              <DollarSign className="text-green-600" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Commission Payable</p>
              <p className="text-xl font-semibold text-[#201515]">{stats.commissions.payable}</p>
              <p className="text-xs text-green-600">ready for payout</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <FolderTree className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Categories</p>
              <p className="text-xl font-semibold text-[#201515]">{categories.length}</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-5">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-[#eceae3] rounded-lg">
              <Users className="text-[#ff4f00]" size={20} />
            </div>
            <div>
              <p className="text-xs text-[#939084]">Drivers</p>
              <p className="text-xl font-semibold text-[#201515]">{stats.drivers.total}</p>
              <p className="text-xs text-green-600">{stats.drivers.online} online</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white border border-[#c5c0b1] rounded-lg p-6">
          <h3 className="text-lg font-semibold text-[#201515] mb-4">Recent Orders</h3>
          {products.length === 0 ? (
            <p className="text-sm text-[#939084]">No orders yet.</p>
          ) : (
            <div className="space-y-3">
              {products.slice(0, 5).map((product) => (
                <div key={product.id} className="flex items-center justify-between py-2 border-b border-[#eceae3] last:border-0">
                  <div>
                    <p className="text-sm font-medium text-[#201515]">{product.name}</p>
                    <p className="text-xs text-[#939084]">{product.code}</p>
                  </div>
                  <p className="text-sm text-[#36342e]">Rs. {parseFloat(product.price).toFixed(2)}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-6">
          <h3 className="text-lg font-semibold text-[#201515] mb-4">Quick Actions</h3>
          <div className="space-y-2">
            <a href="/agents" className="block p-3 rounded-lg border border-[#c5c0b1] hover:border-[#ff4f00] hover:bg-orange-50 transition-colors">
              <p className="text-sm font-medium text-[#201515]">Manage Agents</p>
              <p className="text-xs text-[#939084]">View and onboard affiliate agents</p>
            </a>
            <a href="/orders" className="block p-3 rounded-lg border border-[#c5c0b1] hover:border-[#ff4f00] hover:bg-orange-50 transition-colors">
              <p className="text-sm font-medium text-[#201515]">Manage Orders</p>
              <p className="text-xs text-[#939084]">View orders and track deliveries</p>
            </a>
            <a href="/settings" className="block p-3 rounded-lg border border-[#c5c0b1] hover:border-[#ff4f00] hover:bg-orange-50 transition-colors">
              <p className="text-sm font-medium text-[#201515]">Commission Settings</p>
              <p className="text-xs text-[#939084]">Configure rates and lock periods</p>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}