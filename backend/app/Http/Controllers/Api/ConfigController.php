<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Config;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;

class ConfigController extends Controller
{
    public function index()
    {
        $configs = Config::all()->groupBy(function($config) {
            $category = match(true) {
                str_starts_with($config->key, 'commission_') => 'commission',
                str_starts_with($config->key, 'app_') => 'app',
                str_starts_with($config->key, 'delivery_') => 'delivery',
                str_starts_with($config->key, 'driver_') => 'driver',
                default => 'general',
            };
            return $category;
        });

        return response()->json($configs);
    }

    public function update(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'key' => 'required|string',
            'value' => 'required',
            'type' => 'sometimes|string|in:string,integer,float,boolean,json',
            'description' => 'nullable|string',
        ]);

        $type = $validated['type'] ?? 'string';
        
        $config = Config::updateOrCreate(
            ['key' => $validated['key']],
            [
                'value' => is_array($validated['value']) ? json_encode($validated['value']) : (string) $validated['value'],
                'type' => $type,
                'description' => $validated['description'] ?? null,
            ]
        );

        return response()->json($config);
    }

    public function bulkUpdate(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'configs' => 'required|array',
            'configs.*.key' => 'required|string',
            'configs.*.value' => 'required',
        ]);

        foreach ($validated['configs'] as $item) {
            $type = $item['type'] ?? 'string';
            Config::updateOrCreate(
                ['key' => $item['key']],
                [
                    'value' => is_array($item['value']) ? json_encode($item['value']) : (string) $item['value'],
                    'type' => $type,
                    'description' => $item['description'] ?? null,
                ]
            );
        }

        return response()->json(['message' => 'Configs updated successfully']);
    }

    public function get($key)
    {
        $value = Config::get($key);
        return response()->json(['key' => $key, 'value' => $value]);
    }

    public function seed()
    {
        Config::seedDefaults();
        return response()->json(['message' => 'Default configs seeded']);
    }
}