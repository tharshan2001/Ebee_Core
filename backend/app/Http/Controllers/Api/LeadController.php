<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Lead;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;

class LeadController extends Controller
{
    public function index(Request $request)
    {
        $query = Lead::with(['agent', 'convertedOrder']);

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        if ($request->has('agent_id')) {
            $query->where('agent_id', $request->agent_id);
        }

        $leads = $query->orderBy('created_at', 'desc')->get();
        return response()->json($leads);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'agent_id' => 'required|exists:agents,id',
            'customer_name' => 'required|max:255',
            'customer_phone' => 'required|max:50',
            'customer_email' => 'nullable|email',
            'product_interest' => 'nullable|max:255',
            'notes' => 'nullable',
        ]);

        $lead = Lead::create($validated);
        return response()->json($lead, 201);
    }

    public function show(Lead $lead): JsonResponse
    {
        return response()->json($lead->load(['agent', 'convertedOrder']));
    }

    public function update(Request $request, Lead $lead): JsonResponse
    {
        $validated = $request->validate([
            'customer_name' => 'sometimes|max:255',
            'customer_phone' => 'sometimes|max:50',
            'customer_email' => 'nullable|email',
            'product_interest' => 'nullable|max:255',
            'notes' => 'nullable',
            'status' => 'sometimes|in:pending,converted,expired',
        ]);

        $lead->update($validated);
        return response()->json($lead);
    }

    public function convert(Request $request, Lead $lead): JsonResponse
    {
        $request->validate([
            'order_id' => 'required|exists:orders,id',
        ]);

        $lead->update([
            'status' => 'converted',
            'converted_order_id' => $request->order_id,
            'converted_at' => now(),
        ]);

        return response()->json($lead->load('convertedOrder'));
    }

    public function stats(): JsonResponse
    {
        $stats = [
            'total' => Lead::count(),
            'pending' => Lead::where('status', 'pending')->count(),
            'converted' => Lead::where('status', 'converted')->count(),
            'expired' => Lead::where('status', 'expired')->count(),
        ];

        return response()->json($stats);
    }
}