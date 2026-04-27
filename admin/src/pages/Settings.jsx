import { useState } from 'react';

export default function Settings() {
  const [apiUrl, setApiUrl] = useState('http://localhost:8081/api');

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-2xl font-semibold text-[#201515]">Settings</h2>
        <p className="text-sm text-[#939084] mt-1">Configure application settings</p>
      </div>

      <div className="bg-white border border-[#c5c0b1] rounded-lg p-6 max-w-lg">
        <h3 className="text-lg font-semibold text-[#201515] mb-4">API Configuration</h3>
        
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-[#36342e] mb-1">API Base URL</label>
            <input
              type="text"
              value={apiUrl}
              onChange={(e) => setApiUrl(e.target.value)}
              className="w-full px-3 py-2 border border-[#c5c0b1] rounded-md focus:outline-none focus:border-[#ff4f00]"
            />
          </div>
        </div>

        <div className="mt-6">
          <button className="px-4 py-2 bg-[#ff4f00] text-white rounded-md text-sm font-medium hover:bg-[#e64600]">
            Save Settings
          </button>
        </div>
      </div>
    </div>
  );
}