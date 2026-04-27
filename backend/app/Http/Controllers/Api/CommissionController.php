<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Commission;
use App\Models\Order;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Carbon\Carbon;

class CommissionController extends Controller
{
    public function index(Request $request)
    {
        $query = Commission::with(['agent', 'order']);

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        if ($request->has('agent_id')) {
            $query->where('agent_id', $request->agent_id);
        }

        if ($request->has('date_from')) {
            $query->whereDate('created_at', '>=', $request->date_from);
        }

        if ($request->has('date_to')) {
            $query->whereDate('created_at', '<=', $request->date_to);
        }

        $commissions = $query->orderBy('created_at', 'desc')->get();
        return response()->json($commissions);
    }

    public function show(Commission $commission): JsonResponse
    {
        return response()->json($commission->load(['agent', 'order']));
    }

    public function update(Request $request, Commission $commission): JsonResponse
    {
        $validated = $request->validate([
            'amount' => 'sometimes|numeric|min:0',
            'rate_used' => 'sometimes|numeric|min:0|max:100',
            'status' => 'sometimes|in:pending,locked,payable,paid,reversed',
            'notes' => 'nullable',
        ]);

        $commission->update($validated);
        return response()->json($commission->load(['agent', 'order']));
    }

    public function release(Commission $commission): JsonResponse
    {
        if ($commission->status !== 'locked') {
            return response()->json(['error' => 'Only locked commissions can be released'], 400);
        }

        if (!$commission->isReleasable) {
            return response()->json(['error' => 'Commission is still within 10-day lock period'], 400);
        }

        $commission->update([
            'status' => 'payable',
            'released_at' => now(),
        ]);

        $commission->order->update(['status' => 'commission_released']);

        return response()->json($commission->load(['agent', 'order']));
    }

    public function markPaid(Request $request, Commission $commission): JsonResponse
    {
        $validated = $request->validate([
            'payout_batch_id' => 'nullable|string',
        ]);

        if ($commission->status !== 'payable') {
            return response()->json(['error' => 'Only payable commissions can be marked as paid'], 400);
        }

        $commission->update([
            'status' => 'paid',
            'paid_at' => now(),
            'payout_batch_id' => $validated['payout_batch_id'] ?? null,
        ]);

        $commission->order->update(['status' => 'paid']);

        return response()->json($commission->load(['agent', 'order']));
    }

    public function reverse(Request $request, Commission $commission): JsonResponse
    {
        $validated = $request->validate([
            'reason' => 'required|string',
        ]);

        $newStatus = in_array($commission->status, ['paid']) ? 'reversed' : 'reversed';
        
        $commission->update([
            'status' => $newStatus,
            'notes' => $validated['reason'],
        ]);

        if ($commission->status === 'paid') {
            $commission->order->update(['status' => 'returned']);
        }

        return response()->json($commission->load(['agent', 'order']));
    }

    public function stats(): JsonResponse
    {
        $stats = [
            'total_earned' => Commission::sum('amount'),
            'pending' => Commission::where('status', 'pending')->sum('amount'),
            'locked' => Commission::where('status', 'locked')->sum('amount'),
            'payable' => Commission::where('status', 'payable')->sum('amount'),
            'paid' => Commission::where('status', 'paid')->sum('amount'),
            'reversed' => Commission::where('status', 'reversed')->sum('amount'),
            'count_pending' => Commission::where('status', 'pending')->count(),
            'count_locked' => Commission::where('status', 'locked')->count(),
            'count_payable' => Commission::where('status', 'payable')->count(),
            'count_paid' => Commission::where('status', 'paid')->count(),
        ];

        return response()->json($stats);
    }

    public function liability(): JsonResponse
    {
        $lockedCommissions = Commission::where('status', 'locked')
            ->whereDate('locked_at', '<=', now()->subDays(10))
            ->get();

        $totalLiability = $lockedCommissions->sum('amount');

        return response()->json([
            'total_locked' => Commission::where('status', 'locked')->sum('amount'),
            'releasable_now' => $totalLiability,
            'count_releasable' => $lockedCommissions->count(),
        ]);
    }

    public function payouts(Request $request): JsonResponse
    {
        $query = Commission::where('status', 'payable')
            ->whereNotNull('paid_at')
            ->with(['agent']);

        $payouts = $query->orderBy('paid_at', 'desc')->get();

        $grouped = $payouts->groupBy('payout_batch_id')->map(function ($items) {
            return [
                'batch_id' => $items->first()->payout_batch_id,
                'total' => $items->sum('amount'),
                'count' => $items->count(),
                'paid_at' => $items->first()->paid_at,
                'agents' => $items->groupBy('agent_id')->map(function ($agentItems) {
                    return [
                        'agent_id' => $agentItems->first()->agent_id,
                        'agent_name' => $agentItems->first()->agent?->name,
                        'total' => $agentItems->sum('amount'),
                        'count' => $agentItems->count(),
                    ];
                })->values(),
            ];
        })->values();

        return response()->json($payouts);
    }

    public function processRelease(): JsonResponse
    {
        $released = Commission::where('status', 'locked')
            ->whereDate('locked_at', '<=', now()->subDays(10))
            ->update([
                'status' => 'payable',
                'released_at' => now(),
            ]);

        if ($released > 0) {
            $orderIds = Commission::where('status', 'payable')
                ->whereNotNull('released_at')
                ->pluck('order_id');
            
            Order::whereIn('id', $orderIds)->update(['status' => 'commission_released']);
        }

        return response()->json([
            'message' => "Released {$released} commissions",
            'count' => $released,
        ]);
    }
}