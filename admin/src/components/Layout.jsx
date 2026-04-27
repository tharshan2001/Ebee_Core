import { Outlet, Link, useLocation } from 'react-router-dom';
import { Package, FolderTree, LayoutDashboard, Settings, Users, ShoppingCart } from 'lucide-react';

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/products', label: 'Products', icon: Package },
  { path: '/categories', label: 'Categories', icon: FolderTree },
  { path: '/agents', label: 'Agents', icon: Users },
  { path: '/orders', label: 'Orders', icon: ShoppingCart },
  { path: '/settings', label: 'Settings', icon: Settings },
];

export default function Layout() {
  const location = useLocation();

  return (
    <div className="min-h-screen flex bg-[#fffefb]">
      <aside className="w-56 border-r border-[#c5c0b1] fixed h-full">
        <div className="p-6 border-b border-[#c5c0b1]">
          <h1 className="text-xl font-semibold text-[#201515]" style={{ fontFamily: 'Degular Display, sans-serif' }}>
            EasyCatalog
          </h1>
        </div>
        <nav className="p-4">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-lg mb-1 transition-colors ${
                  isActive
                    ? 'bg-[#ff4f00] text-white'
                    : 'text-[#36342e] hover:bg-[#eceae3]'
                }`}
              >
                <Icon size={18} />
                <span className="text-sm font-medium">{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </aside>
      <main className="flex-1 ml-56">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}