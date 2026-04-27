import { useEffect, useState } from 'react';
import { Save, RotateCcw, Settings as SettingsIcon, DollarSign, Lock, Truck } from 'lucide-react';
import api from '../services/api';

export default function Settings() {
  const [configs, setConfigs] = useState({
    commission: {},
    app: {},
    delivery: {},
    driver: {},
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const defaultConfigs = [
    { key: 'commission_default_rate', value: 5, type: 'float', description: 'Default commission rate (%)', category: 'commission', icon: DollarSign },
    { key: 'commission_performance_rate', value: 7, type: 'float', description: 'Performance tier rate (%)', category: 'commission', icon: DollarSign },
    { key: 'commission_performance_threshold', value: 10000, type: 'float', description: 'Monthly threshold for performance tier', category: 'commission', icon: DollarSign },
    { key: 'commission_lock_days', value: 10, type: 'integer', description: 'Days to lock commission after delivery', category: 'commission', icon: Lock },
    { key: 'app_name', value: 'Ebee', type: 'string', description: 'Application name', category: 'app', icon: SettingsIcon },
    { key: 'app_currency', value: 'LKR', type: 'string', description: 'Currency code', category: 'app', icon: SettingsIcon },
    { key: 'app_currency_symbol', value: 'Rs.', type: 'string', description: 'Currency symbol', category: 'app', icon: SettingsIcon },
  ];

  useEffect(() => {
    fetchConfigs();
  }, []);

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      const response = await api.get('/configs');
      const grouped = { commission: {}, app: {}, delivery: {}, driver: {}, general: {} };
      
      defaultConfigs.forEach(cfg => {
        const found = response.data.find(c => c.key === cfg.key);
        grouped[cfg.category][cfg.key] = found ? found.value : cfg.value;
      });
      
      setConfigs(grouped);
    } catch (err) {
      console.error(err);
      defaultConfigs.forEach(cfg => {
        if (!configs[cfg.category][cfg.key]) {
          setConfigs(prev => ({
            ...prev,
            [cfg.category]: { ...prev[cfg.category], [cfg.key]: cfg.value }
          }));
        }
      });
    }
    setLoading(false);
  };

  const updateConfig = (category, key, value) => {
    setConfigs(prev => ({
      ...prev,
      [category]: { ...prev[category], [key]: value }
    }));
    setSaved(false);
  };

  const saveConfigs = async () => {
    setSaving(true);
    try {
      const configsToUpdate = [];
      
      Object.entries(configs.commission).forEach(([key, value]) => {
        configsToUpdate.push({ key, value, type: defaultConfigs.find(c => c.key === key)?.type || 'string' });
      });
      Object.entries(configs.app).forEach(([key, value]) => {
        configsToUpdate.push({ key, value, type: defaultConfigs.find(c => c.key === key)?.type || 'string' });
      });

      await api.patch('/configs/bulk', { configs: configsToUpdate });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (err) {
      alert('Error saving configs');
    }
    setSaving(false);
  };

  const resetToDefaults = async () => {
    if (confirm('Reset all configs to defaults?')) {
      try {
        await api.post('/configs/seed');
        fetchConfigs();
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
      } catch (err) {
        alert('Error resetting configs');
      }
    }
  };

  const categories = [
    { key: 'commission', label: 'Commission Settings', icon: DollarSign },
    { key: 'app', label: 'App Settings', icon: SettingsIcon },
  ];

  if (loading) {
    return <div className="p-8 text-[#939084]">Loading...</div>;
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-semibold text-[#201515]">Settings</h2>
          <p className="text-sm text-[#939084] mt-1">Configure application settings & commission engine</p>
        </div>
        <div className="flex items-center gap-3">
          {saved && (
            <span className="text-sm text-green-600">Saved!</span>
          )}
          <button
            onClick={resetToDefaults}
            className="flex items-center gap-2 px-3 py-2 border border-[#c5c0b1] rounded-md text-sm font-medium text-[#36342e] hover:bg-[#eceae3]"
          >
            <RotateCcw size={16} />
            Reset
          </button>
          <button
            onClick={saveConfigs}
            disabled={saving}
            className="flex items-center gap-2 px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600] disabled:opacity-50"
          >
            <Save size={16} />
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {categories.map(cat => (
          <div key={cat.key} className="bg-white border border-[#c5c0b1] rounded-lg p-6">
            <div className="flex items-center gap-2 mb-4">
              <cat.icon size={20} className="text-[#ff4f00]" />
              <h3 className="text-lg font-semibold text-[#201515]">{cat.label}</h3>
            </div>

            <div className="space-y-4">
              {defaultConfigs
                .filter(cfg => cfg.category === cat.key)
                .map(cfg => (
                  <div key={cfg.key}>
                    <label className="block text-sm font-medium text-[#36342e] mb-1">
                      {cfg.description}
                    </label>
                    {cfg.type === 'boolean' ? (
                      <label className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={configs[cat.key]?.[cfg.key] === 'true' || configs[cat.key]?.[cfg.key] === true}
                          onChange={(e) => updateConfig(cat.key, cfg.key, e.target.checked)}
                          className="w-4 h-4 text-[#ff4f00] border-[#c5c0b1] rounded focus:ring-[#ff4f00]"
                        />
                        <span className="text-sm text-[#36342e]">Enabled</span>
                      </label>
                    ) : (
                      <input
                        type={cfg.type === 'integer' || cfg.type === 'float' ? 'number' : 'text'}
                        value={configs[cat.key]?.[cfg.key] ?? cfg.value}
                        onChange={(e) => updateConfig(cat.key, cfg.key, e.target.value)}
                        className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
                      />
                    )}
                  </div>
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}