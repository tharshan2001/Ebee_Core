<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Agent;
use App\Models\Delivery;
use App\Models\Commission;
use App\Models\Driver;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;

class OrderController extends Controller
{
    public function index(Request $request)
    {
        $query = Order::with(['agent', 'delivery.driver']);

        if ($request->has('date_from')) {
            $query->whereDate('created_at', '>=', $request->date_from);
        }
        if ($request->has('date_to')) {
            $query->whereDate('created_at', '<=', $request->date_to);
        }
        if ($request->has('agent_id')) {
            $query->where('agent_code_used', $request->agent_id);
        }
        if ($request->has('status')) {
            $query->where('status', $request->status);
        }
        if ($request->has('delivery_zone')) {
            $query->where('delivery_zone', $request->delivery_zone);
        }
        if ($request->has('search')) {
            $query->where(function ($q) use ($request) {
                $q->where('order_number', 'like', "%{$request->search}%")
                  ->orWhere('customer_name', 'like', "%{$request->search}%")
                  ->orWhere('customer_phone', 'like', "%{$request->search}%");
            });
        }

        $orders = $query->orderBy('created_at', 'desc')->get();
        return response()->json($orders);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'customer_name' => 'required|max:255',
            'customer_phone' => 'required|max:50',
            'customer_email' => 'nullable|email',
            'customer_address' => 'required',
            'delivery_zone' => 'nullable|max:100',
            'agent_code_used' => 'nullable|exists:agents,code',
            'total_value' => 'required|numeric|min:0',
            'product_name' => 'nullable|max:255',
            'product_sku' => 'nullable|max:100',
            'attribution_note' => 'nullable',
        ]);

        $validated['status'] = 'pending';

        $order = Order::create($validated);

        if ($validated['agent_code_used']) {
            $agent = Agent::where('code', $validated['agent_code_used'])->first();
            if ($agent) {
                $rate = $agent->commission_rate;
                $amount = ($validated['total_value'] * $rate) / 100;

                Commission::create([
                    'agent_id' => $agent->id,
                    'order_id' => $order->id,
                    'amount' => $amount,
                    'rate_used' => $rate,
                    'status' => 'pending',
                ]);
            }
        }

        return response()->json($order->load(['agent', 'delivery', 'commission']), 201);
    }

    public function show(Order $order): JsonResponse
    {
        return response()->json($order->load(['agent', 'delivery.driver', 'commission']));
    }

    public function update(Request $request, Order $order): JsonResponse
    {
        $validated = $request->validate([
            'customer_name' => 'sometimes|max:255',
            'customer_phone' => 'sometimes|max:50',
            'customer_email' => 'nullable|email',
            'customer_address' => 'sometimes',
            'delivery_zone' => 'nullable|max:100',
            'agent_code_used' => 'nullable|exists:agents,code',
            'total_value' => 'sometimes|numeric|min:0',
            'product_name' => 'nullable|max:255',
            'product_sku' => 'nullable|max:100',
            'status' => 'sometimes|in:pending,processing,assigned,out_for_delivery,delivered,commission_locked,commission_released,paid,returned,cancelled',
            'attribution_note' => 'nullable',
            'attribution_resolved_by' => 'nullable',
            'attribution_resolved_at' => 'nullable',
        ]);

        $oldAgentCode = $order->agent_code_used;
        $order->update($validated);

        if (isset($validated['agent_code_used']) && $validated['agent_code_used'] !== $oldAgentCode) {
            $this->reassignCommission($order, $validated['agent_code_used']);
        }

        return response()->json($order->load(['agent', 'delivery', 'commission']));
    }

    public function destroy(Order $order): JsonResponse
    {
        $order->delete();
        return response()->json(null, 204);
    }

    public function timeline(Order $order): JsonResponse
    {
        return response()->json([
            'order' => $order->order_number,
            'timeline' => $order->timeline,
        ]);
    }

    public function assignDriver(Request $request, Order $order): JsonResponse
    {
        $validated = $request->validate([
            'driver_id' => 'required|exists:drivers,id',
        ]);

        $driver = Driver::findOrFail($validated['driver_id']);

        $delivery = Delivery::updateOrCreate(
            ['order_id' => $order->id],
            [
                'driver_id' => $driver->id,
                'status' => 'assigned',
                'assigned_at' => now(),
            ]
        );

        $order->update(['status' => 'assigned']);

        return response()->json($delivery->load(['order', 'driver']));
    }

    public function resolveAttribution(Request $request, Order $order): JsonResponse
    {
        $validated = $request->validate([
            'agent_code_used' => 'required|exists:agents,code',
            'attribution_note' => 'required',
            'attribution_resolved_by' => 'required',
        ]);

        $oldAgentCode = $order->agent_code_used;
        $order->update([
            'agent_code_used' => $validated['agent_code_used'],
            'attribution_note' => $validated['attribution_note'],
            'attribution_resolved_by' => $validated['attribution_resolved_by'],
            'attribution_resolved_at' => now(),
        ]);

        if ($oldAgentCode !== $validated['agent_code_used']) {
            $this->reassignCommission($order, $validated['agent_code_used']);
        }

        return response()->json($order->load(['agent', 'commission']));
    }

    private function reassignCommission(Order $order, ?string $agentCode): void
    {
        $order->commission?->delete();

        if ($agentCode) {
            $agent = Agent::where('code', $agentCode)->first();
            if ($agent) {
                $rate = $agent->commission_rate;
                $amount = ($order->total_value * $rate) / 100;

                Commission::create([
                    'agent_id' => $agent->id,
                    'order_id' => $order->id,
                    'amount' => $amount,
                    'rate_used' => $rate,
                    'status' => 'pending',
                ]);
            }
        }
    }

    public function stats(): JsonResponse
    {
        $stats = [
            'total' => Order::count(),
            'pending' => Order::where('status', 'pending')->count(),
            'processing' => Order::where('status', 'processing')->count(),
            'delivered' => Order::where('status', 'delivered')->count(),
            'commission_locked' => Order::where('status', 'commission_locked')->count(),
            'commission_released' => Order::where('status', 'commission_released')->count(),
            'paid' => Order::where('status', 'paid')->count(),
            'returned' => Order::where('status', 'returned')->count(),
            'total_value' => Order::sum('total_value'),
        ];

        return response()->json($stats);
    }

    public function zones(): JsonResponse
    {
        $zones = Order::whereNotNull('delivery_zone')
            ->distinct()
            ->pluck('delivery_zone');

        return response()->json($zones);
    }

    public function placeOrder(Request $request): JsonResponse
    {
        $request->validate([
            'product_sku' => 'required|max:100',
            'product_name' => 'nullable|max:255',
            'total_value' => 'required|numeric|min:0',
            'customer_name' => 'required|max:255',
            'customer_phone' => 'required|max:50',
            'customer_email' => 'nullable|email',
            'customer_address' => 'required',
            'delivery_zone' => 'nullable|max:100',
        ]);

        $agent = $request->user();

        $validated = $request->all();
        $validated['agent_code_used'] = $agent->code;
        $validated['status'] = 'pending';
        $validated['order_number'] = 'ORD-' . date('Ymd') . '-' . strtoupper(uniqid());

        $order = Order::create($validated);

        $rate = $agent->commission_rate;
        $amount = ($validated['total_value'] * $rate) / 100;

        Commission::create([
            'agent_id' => $agent->id,
            'order_id' => $order->id,
            'amount' => $amount,
            'rate_used' => $rate,
            'status' => 'pending',
        ]);

        $order->load(['agent', 'commission']);

        return response()->json([
            'message' => 'Order placed successfully',
            'order' => $order,
            'commission' => $amount,
        ], 201);
    }

    public function myOrders(Request $request): JsonResponse
    {
        $agent = $request->user();
        
        $query = Order::where('agent_code_used', $agent->code)
            ->with(['commission']);

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        $orders = $query->orderBy('created_at', 'desc')->get();

        return response()->json($orders);
    }
}