import { useEffect, useState } from 'react';
import { Plus, Edit, Trash2, Search } from 'lucide-react';
import { useCategoryStore } from '../stores/categoryStore';

export default function Categories() {
  const { categories, loading, fetchCategories, addCategory, updateCategory, deleteCategory } =
    useCategoryStore();
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    slug: '',
  });

  useEffect(() => {
    fetchCategories();
  }, []);

  const filteredCategories = categories.filter((c) =>
    c.name.toLowerCase().includes(search.toLowerCase())
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editingCategory) {
        await updateCategory(editingCategory.id, formData);
      } else {
        await addCategory(formData);
      }
      setShowModal(false);
      resetForm();
      fetchCategories();
    } catch (err) {
      alert(err.response?.data?.message || 'Error saving category');
    }
  };

  const handleDelete = async (id) => {
    if (confirm('Are you sure you want to delete this category?')) {
      try {
        await deleteCategory(id);
      } catch (err) {
        alert('Error deleting category');
      }
    }
  };

  const resetForm = () => {
    setFormData({ name: '', slug: '' });
    setEditingCategory(null);
  };

  const openEdit = (category) => {
    setEditingCategory(category);
    setFormData({ name: category.name, slug: category.slug });
    setShowModal(true);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-semibold text-[#201515]">Categories</h2>
          <p className="text-sm text-[#939084] mt-1">Manage product categories</p>
        </div>
        <button
          onClick={() => {
            resetForm();
            setShowModal(true);
          }}
          className="flex items-center gap-2 px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600] transition-colors"
        >
          <Plus size={18} />
          Add Category
        </button>
      </div>

      <div className="mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#939084]" size={18} />
          <input
            type="text"
            placeholder="Search categories..."
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
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Name</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Slug</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Products</th>
              <th className="text-right px-4 py-3 text-sm font-semibold text-[#36342e]">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-[#939084]">
                  Loading...
                </td>
              </tr>
            ) : filteredCategories.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-[#939084]">
                  No categories found
                </td>
              </tr>
            ) : (
              filteredCategories.map((category) => (
                <tr key={category.id} className="border-t border-[#c5c0b1]">
                  <td className="px-4 py-3 text-sm font-medium text-[#201515]">{category.name}</td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">{category.slug}</td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">
                    {category.products?.length || 0} products
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEdit(category)}
                      className="inline-flex p-2 text-[#36342e] hover:text-[#ff4f00] transition-colors"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(category.id)}
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
          <div className="bg-white rounded-lg w-full max-w-md mx-4 border border-[#c5c0b1]">
            <div className="px-6 py-4 border-b border-[#c5c0b1]">
              <h3 className="text-lg font-semibold text-[#201515]">
                {editingCategory ? 'Edit Category' : 'Add Category'}
              </h3>
            </div>
            <form onSubmit={handleSubmit} className="p-6">
              <div className="space-y-4">
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
                  {editingCategory ? 'Update' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}