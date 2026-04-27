import { Package, FolderTree, TrendingUp } from 'lucide-react';
import { useProductStore } from '../stores/productStore';
import { useCategoryStore } from '../stores/categoryStore';
import { useEffect } from 'react';

export default function Dashboard() {
  const { products, fetchProducts } = useProductStore();
  const { categories, fetchCategories } = useCategoryStore();

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  const activeProducts = products.filter((p) => !p.archived).length;
  const archivedProducts = products.filter((p) => p.archived).length;

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-2xl font-semibold text-[#201515]">Dashboard</h2>
        <p className="text-sm text-[#939084] mt-1">Welcome to EasyCatalog admin</p>
      </div>

      <div className="grid grid-cols-3 gap-6">
        <div className="bg-white border border-[#c5c0b1] rounded-lg p-6">
          <div className="flex items-center gap-4">
            <div className="p-3 bg-[#eceae3] rounded-lg">
              <Package className="text-[#ff4f00]" size={24} />
            </div>
            <div>
              <p className="text-sm text-[#939084]">Total Products</p>
              <p className="text-2xl font-semibold text-[#201515]">{products.length}</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-6">
          <div className="flex items-center gap-4">
            <div className="p-3 bg-[#eceae3] rounded-lg">
              <TrendingUp className="text-[#ff4f00]" size={24} />
            </div>
            <div>
              <p className="text-sm text-[#939084]">Active Products</p>
              <p className="text-2xl font-semibold text-[#201515]">{activeProducts}</p>
            </div>
          </div>
        </div>

        <div className="bg-white border border-[#c5c0b1] rounded-lg p-6">
          <div className="flex items-center gap-4">
            <div className="p-3 bg-[#eceae3] rounded-lg">
              <FolderTree className="text-[#ff4f00]" size={24} />
            </div>
            <div>
              <p className="text-sm text-[#939084]">Categories</p>
              <p className="text-2xl font-semibold text-[#201515]">{categories.length}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-8 bg-white border border-[#c5c0b1] rounded-lg p-6">
        <h3 className="text-lg font-semibold text-[#201515] mb-4">Recent Products</h3>
        {products.length === 0 ? (
          <p className="text-sm text-[#939084]">No products yet. Add your first product!</p>
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
    </div>
  );
}