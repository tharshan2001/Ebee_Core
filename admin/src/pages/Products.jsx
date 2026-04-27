import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Edit, Trash2, Search, ImageIcon } from 'lucide-react';
import { useProductStore } from '../stores/productStore';
import { useCategoryStore } from '../stores/categoryStore';

export default function Products() {
  const { products, loading, error, fetchProducts, deleteProduct } = useProductStore();
  const { categories, fetchCategories } = useCategoryStore();
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    slug: '',
    image_url: '',
    tags: '',
    category_id: '',
    archived: false,
    price: '',
  });

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  const filteredProducts = products.filter(
    (p) =>
      p.name.toLowerCase().includes(search.toLowerCase()) ||
      p.code.toLowerCase().includes(search.toLowerCase())
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    const data = {
      ...formData,
      tags: formData.tags ? formData.tags.split(',').map((t) => t.trim()) : [],
      price: parseFloat(formData.price),
    };

    try {
      if (editingProduct) {
        await useProductStore.getState().updateProduct(editingProduct.id, data);
      } else {
        await useProductStore.getState().addProduct(data);
      }
      setShowModal(false);
      resetForm();
      fetchProducts();
    } catch (err) {
      alert(err.response?.data?.message || 'Error saving product');
    }
  };

  const handleDelete = async (id) => {
    if (confirm('Are you sure you want to delete this product?')) {
      try {
        await deleteProduct(id);
      } catch (err) {
        alert('Error deleting product');
      }
    }
  };

  const resetForm = () => {
    setFormData({
      code: '',
      name: '',
      slug: '',
      image_url: '',
      tags: '',
      category_id: '',
      archived: false,
      price: '',
    });
    setEditingProduct(null);
  };

  const openEdit = (product) => {
    setEditingProduct(product);
    setFormData({
      code: product.code,
      name: product.name,
      slug: product.slug,
      image_url: product.image_url || '',
      tags: product.tags?.join(', ') || '',
      category_id: product.category_id || '',
      archived: product.archived,
      price: product.price,
    });
    setShowModal(true);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-semibold text-[#201515]">Products</h2>
          <p className="text-sm text-[#939084] mt-1">Manage your product catalog</p>
        </div>
        <button
          onClick={() => {
            resetForm();
            setShowModal(true);
          }}
          className="flex items-center gap-2 px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600] transition-colors"
        >
          <Plus size={18} />
          Add Product
        </button>
      </div>

      <div className="mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#939084]" size={18} />
          <input
            type="text"
            placeholder="Search products..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full max-w-md pl-10 pr-4 py-2 border border-[#c5c0b1] rounded-md bg-white text-[#36342e] placeholder-[#939084] focus:outline-none focus:border-[#ff4f00]"
          />
        </div>
      </div>

      <div className="bg-white border border-[#c5c0b1] rounded-lg overflow-hidden">
        <table className="w-full">
          <thead className="bg-[#eceae3]">
            <tr>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Image</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Code</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Name</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Category</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Price</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Status</th>
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
            ) : filteredProducts.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-[#939084]">
                  No products found
                </td>
              </tr>
            ) : (
              filteredProducts.map((product) => (
                <tr key={product.id} className="border-t border-[#c5c0b1]">
                  <td className="px-4 py-3">
                    {product.image_url ? (
                      <img
                        src={product.image_url}
                        alt={product.name}
                        className="w-12 h-12 object-cover rounded border border-[#c5c0b1]"
                      />
                    ) : (
                      <div className="w-12 h-12 bg-[#eceae3] rounded border border-[#c5c0b1] flex items-center justify-center">
                        <ImageIcon size={18} className="text-[#939084]" />
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">{product.code}</td>
                  <td className="px-4 py-3 text-sm font-medium text-[#201515]">{product.name}</td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">
                    {product.category?.name || '-'}
                  </td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">
                    Rs. {parseFloat(product.price).toFixed(2)}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-block px-2 py-1 text-xs rounded ${
                        product.archived
                          ? 'bg-[#eceae3] text-[#939084]'
                          : 'bg-[#eceae3] text-[#36342e]'
                      }`}
                    >
                      {product.archived ? 'Archived' : 'Active'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEdit(product)}
                      className="inline-flex p-2 text-[#36342e] hover:text-[#ff4f00] transition-colors"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(product.id)}
                      className="inline-flex p-2 text-[#36342e] hover:text-red-600 transition-colors ml-1"
                    >
                      <Trash2 size={16} />
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
          <div className="bg-white rounded-lg w-full max-w-lg mx-4 border border-[#c5c0b1]">
            <div className="px-6 py-4 border-b border-[#c5c0b1]">
              <h3 className="text-lg font-semibold text-[#201515]">
                {editingProduct ? 'Edit Product' : 'Add Product'}
              </h3>
            </div>
            <form onSubmit={handleSubmit} className="p-6">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Code</label>
                  <input
                    type="text"
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Name</label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        name: e.target.value,
                        slug: e.target.value.toLowerCase().replace(/\s+/g, '-'),
                      })
                    }
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Slug</label>
                  <input
                    type="text"
                    value={formData.slug}
                    onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Price</label>
                  <input
                    type="number"
                    step="0.01"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Category</label>
                  <select
                    value={formData.category_id}
                    onChange={(e) => setFormData({ ...formData, category_id: e.target.value })}
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                  >
                    <option value="">Select category</option>
                    {categories.map((cat) => (
                      <option key={cat.id} value={cat.id}>
                        {cat.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-[#36342e] mb-1">Image URL</label>
                  <input
                    type="text"
                    value={formData.image_url}
                    onChange={(e) => setFormData({ ...formData, image_url: e.target.value })}
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-[#36342e] mb-1">
                    Tags (comma separated)
                  </label>
                  <input
                    type="text"
                    value={formData.tags}
                    onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                    placeholder="tag1, tag2, tag3"
                    className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                  />
                </div>
                <div className="col-span-2">
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={formData.archived}
                      onChange={(e) => setFormData({ ...formData, archived: e.target.checked })}
                      className="rounded border-[#c5c0b1]"
                    />
                    <span className="text-sm text-[#36342e]">Archived</span>
                  </label>
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => {
                    setShowModal(false);
                    resetForm();
                  }}
                  className="px-4 py-2 border border-[#c5c0b1] rounded-md text-sm font-medium text-[#36342e] hover:bg-[#eceae3]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600]"
                >
                  {editingProduct ? 'Update' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}