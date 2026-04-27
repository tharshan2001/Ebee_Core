<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Driver;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Hash;

class DriverController extends Controller
{
    public function index(Request $request)
    {
        $query = Driver::query();

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        $drivers = $query->orderBy('name')->get();
        return response()->json($drivers);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'required|max:255',
            'phone' => 'required|unique:drivers',
            'vehicle_type' => 'nullable|max:100',
            'password' => 'required|min:6',
        ]);

        $validated['password'] = Hash::make($validated['password']);
        $validated['status'] = 'offline';

        $driver = Driver::create($validated);
        return response()->json($driver, 201);
    }

    public function show(Driver $driver): JsonResponse
    {
        return response()->json($driver->load('deliveries'));
    }

    public function update(Request $request, Driver $driver): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'sometimes|max:255',
            'phone' => 'sometimes|unique:drivers,phone,' . $driver->id,
            'vehicle_type' => 'nullable|max:100',
            'status' => 'sometimes|in:offline,online,on_duty',
            'password' => 'sometimes|min:6',
        ]);

        if (isset($validated['password'])) {
            $validated['password'] = Hash::make($validated['password']);
        }

        $driver->update($validated);
        return response()->json($driver);
    }

    public function destroy(Driver $driver): JsonResponse
    {
        $driver->delete();
        return response()->json(null, 204);
    }

    public function toggleStatus(Driver $driver): JsonResponse
    {
        $statuses = ['offline' => 'online', 'online' => 'offline', 'on_duty' => 'offline'];
        $newStatus = $statuses[$driver->status] ?? 'offline';
        $driver->update(['status' => $newStatus]);
        return response()->json($driver);
    }

    public function login(Request $request): JsonResponse
    {
        $request->validate([
            'phone' => 'required',
            'password' => 'required',
        ]);

        $driver = Driver::where('phone', $request->phone)->first();

        if (!$driver || !Hash::check($request->password, $driver->password)) {
            return response()->json(['error' => 'Invalid credentials'], 401);
        }

        $token = $driver->createToken('driver-mobile')->plainTextToken;

        return response()->json([
            'driver' => $driver,
            'token' => $token,
        ]);
    }

    public function updateLocation(Request $request): JsonResponse
    {
        $request->validate(['lat' => 'required', 'lng' => 'required']);

        $request->user()->update([
            'current_lat' => $request->lat,
            'current_lng' => $request->lng,
        ]);

        return response()->json(['message' => 'Location updated']);
    }

    public function myDeliveries(Request $request): JsonResponse
    {
        $driver = $request->user();

        $deliveries = \App\Models\Delivery::where('driver_id', $driver->id)
            ->with(['order', 'order.agent'])
            ->orderBy('assigned_at', 'desc')
            ->get();

        return response()->json($deliveries);
    }
}