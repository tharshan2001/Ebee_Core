import { useEffect, useState } from 'react';
import { Plus, Edit, Trash2, Search, UserCheck, UserX } from 'lucide-react';
import { useAgentStore } from '../stores/agentStore';

const COMMISSION_TIERS = [
  { value: 'default', label: 'Default (5%)', rate: 5 },
  { value: 'performance', label: 'Performance (7%)', rate: 7 },
  { value: 'custom', label: 'Custom Override', rate: 0 },
];

export default function Agents() {
  const { agents, loading, fetchAgents, addAgent, updateAgent, deleteAgent, toggleAgentStatus } =
    useAgentStore();
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingAgent, setEditingAgent] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone: '',
    commission_tier: 'default',
    custom_rate: '',
    bank_name: '',
    account_number: '',
    branch_code: '',
    tax_id: '',
    status: 'active',
  });

  useEffect(() => {
    fetchAgents();
  }, []);

  const filteredAgents = agents.filter((a) =>
    a.name?.toLowerCase().includes(search.toLowerCase()) ||
    a.email?.toLowerCase().includes(search.toLowerCase()) ||
    a.code?.toLowerCase().includes(search.toLowerCase())
  );

  const generateReferralCode = (name) => {
    const clean = name.replace(/[^a-zA-Z]/g, '').toUpperCase();
    const base = clean.slice(0, 5) || 'AGENT';
    const random = Math.floor(Math.random() * 9000) + 1000;
    return `${base}${random}`;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        ...formData,
        custom_rate: formData.custom_rate ? parseFloat(formData.custom_rate) : null,
        code: generateReferralCode(formData.name),
        referral_link: `shop/${generateReferralCode(formData.name)}`,
      };
      
      if (editingAgent) {
        await updateAgent(editingAgent.id, payload);
      } else {
        await addAgent(payload);
      }
      setShowModal(false);
      resetForm();
      fetchAgents();
    } catch (err) {
      alert(err.response?.data?.message || 'Error saving agent');
    }
  };

  const handleDelete = async (id) => {
    if (confirm('Are you sure you want to delete this agent?')) {
      try {
        await deleteAgent(id);
      } catch (err) {
        alert('Error deleting agent');
      }
    }
  };

  const handleToggleStatus = async (id) => {
    try {
      await toggleAgentStatus(id);
      fetchAgents();
    } catch (err) {
      alert('Error updating status');
    }
  };

  const resetForm = () => {
    setFormData({
      name: '',
      email: '',
      phone: '',
      commission_tier: 'default',
      custom_rate: '',
      bank_name: '',
      account_number: '',
      branch_code: '',
      tax_id: '',
      status: 'active',
    });
    setEditingAgent(null);
  };

  const openEdit = (agent) => {
    setEditingAgent(agent);
    setFormData({
      name: agent.name || '',
      email: agent.email || '',
      phone: agent.phone || '',
      commission_tier: agent.commission_tier || 'default',
      custom_rate: agent.custom_rate || '',
      bank_name: agent.bank_name || '',
      account_number: agent.account_number || '',
      branch_code: agent.branch_code || '',
      tax_id: agent.tax_id || '',
      status: agent.status || 'active',
    });
    setShowModal(true);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-semibold text-[#201515]">Agents</h2>
          <p className="text-sm text-[#939084] mt-1">Manage affiliate agents & commission tiers</p>
        </div>
        <button
          onClick={() => {
            resetForm();
            setShowModal(true);
          }}
          className="flex items-center gap-2 px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600] transition-colors"
        >
          <Plus size={18} />
          Add Agent
        </button>
      </div>

      <div className="mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[#939084]" size={18} />
          <input
            type="text"
            placeholder="Search by name, email, or code..."
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
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Agent</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Code</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Tier</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Phone</th>
              <th className="text-left px-4 py-3 text-sm font-semibold text-[#36342e]">Status</th>
              <th className="text-right px-4 py-3 text-sm font-semibold text-[#36342e]">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-[#939084]">
                  Loading...
                </td>
              </tr>
            ) : filteredAgents.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-[#939084]">
                  No agents found
                </td>
              </tr>
            ) : (
              filteredAgents.map((agent) => (
                <tr key={agent.id} className="border-t border-[#c5c0b1]">
                  <td className="px-4 py-3">
                    <div>
                      <p className="text-sm font-medium text-[#201515]">{agent.name}</p>
                      <p className="text-xs text-[#939084]">{agent.email}</p>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-sm font-mono bg-[#eceae3] px-2 py-1 rounded text-[#36342e]">
                      {agent.code || 'N/A'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">
                    {agent.commission_tier === 'custom' 
                      ? `${agent.custom_rate}%` 
                      : COMMISSION_TIERS.find(t => t.value === agent.commission_tier)?.label || '5%'}
                  </td>
                  <td className="px-4 py-3 text-sm text-[#36342e]">{agent.phone}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${
                        agent.status === 'active'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {agent.status === 'active' ? 'Active' : 'Suspended'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => handleToggleStatus(agent.id)}
                      className={`inline-flex p-2 transition-colors ${
                        agent.status === 'active'
                          ? 'text-[#36342e] hover:text-[#ff4f00]'
                          : 'text-green-600 hover:text-green-700'
                      }`}
                      title={agent.status === 'active' ? 'Suspend' : 'Activate'}
                    >
                      {agent.status === 'active' ? <UserX size={16} /> : <UserCheck size={16} />}
                    </button>
                    <button
                      onClick={() => openEdit(agent)}
                      className="inline-flex p-2 text-[#36342e] hover:text-[#ff4f00] transition-colors"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(agent.id)}
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
          <div className="bg-white rounded-lg w-full max-w-lg mx-4 border border-[#c5c0b1] max-h-[90vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-[#c5c0b1] sticky top-0 bg-white">
              <h3 className="text-lg font-semibold text-[#201515]">
                {editingAgent ? 'Edit Agent' : 'Agent Onboarding'}
              </h3>
              <p className="text-sm text-[#939084]">
                {editingAgent ? 'Update agent details' : 'Register a new affiliate agent'}
              </p>
            </div>
            <form onSubmit={handleSubmit} className="p-6">
              <div className="space-y-6">
                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Personal Details</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Full Name *</label>
                      <input
                        type="text"
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Email *</label>
                      <input
                        type="email"
                        value={formData.email}
                        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Phone Number *</label>
                      <input
                        type="tel"
                        value={formData.phone}
                        onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        required
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Tax ID (Optional)</label>
                      <input
                        type="text"
                        value={formData.tax_id}
                        onChange={(e) => setFormData({ ...formData, tax_id: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                        placeholder="National ID/ Tax number"
                      />
                    </div>
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Commission Settings</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Commission Tier</label>
                      <select
                        value={formData.commission_tier}
                        onChange={(e) => setFormData({ ...formData, commission_tier: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      >
                        {COMMISSION_TIERS.map((tier) => (
                          <option key={tier.value} value={tier.value}>
                            {tier.label}
                          </option>
                        ))}
                      </select>
                    </div>
                    {formData.commission_tier === 'custom' && (
                      <div>
                        <label className="block text-sm font-medium text-[#36342e] mb-1">Custom Rate (%)</label>
                        <input
                          type="number"
                          step="0.1"
                          min="0"
                          max="100"
                          value={formData.custom_rate}
                          onChange={(e) => setFormData({ ...formData, custom_rate: e.target.value })}
                          className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                          placeholder="e.g., 10"
                        />
                      </div>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-[#201515] mb-3">Bank Details for Payouts</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Bank Name</label>
                      <input
                        type="text"
                        value={formData.bank_name}
                        onChange={(e) => setFormData({ ...formData, bank_name: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Account Number</label>
                      <input
                        type="text"
                        value={formData.account_number}
                        onChange={(e) => setFormData({ ...formData, account_number: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-[#36342e] mb-1">Branch Code</label>
                      <input
                        type="text"
                        value={formData.branch_code}
                        onChange={(e) => setFormData({ ...formData, branch_code: e.target.value })}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-[#c5c0b1]">
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
                  {editingAgent ? 'Update Agent' : 'Register Agent'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}