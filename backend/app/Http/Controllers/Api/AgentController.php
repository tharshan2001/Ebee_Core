<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Agent;
use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class AgentController extends Controller
{
    public function index(Request $request)
    {
        $query = Agent::query();

        if ($request->has('search')) {
            $search = $request->search;
            $query->where(function ($q) use ($search) {
                $q->where('name', 'like', "%{$search}%")
                  ->orWhere('email', 'like', "%{$search}%")
                  ->orWhere('code', 'like', "%{$search}%");
            });
        }

        if ($request->has('status')) {
            $query->where('status', $request->status);
        }

        $agents = $query->orderBy('created_at', 'desc')->get();

        return response()->json($agents);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'required|max:255',
            'email' => 'required|email|unique:agents',
            'phone' => 'required|max:50',
            'code' => 'sometimes|unique:agents',
            'referral_link' => 'sometimes',
            'commission_tier' => 'sometimes|in:default,performance,custom',
            'custom_rate' => 'nullable|numeric|min:0|max:100',
            'bank_name' => 'nullable|max:255',
            'account_number' => 'nullable|max:100',
            'branch_code' => 'nullable|max:50',
            'tax_id' => 'nullable|max:100',
            'status' => 'sometimes|in:active,suspended',
            'password' => 'sometimes|min:6',
        ]);

        if (isset($validated['password'])) {
            $validated['password'] = Hash::make($validated['password']);
        }

        $agent = Agent::create($validated);
        return response()->json($agent, 201);
    }

    public function show(Agent $agent): JsonResponse
    {
        return response()->json($agent->load(['orders', 'commissions']));
    }

    public function update(Request $request, Agent $agent): JsonResponse
    {
        $validated = $request->validate([
            'name' => 'sometimes|max:255',
            'email' => 'sometimes|email|unique:agents,email,' . $agent->id,
            'phone' => 'sometimes|max:50',
            'commission_tier' => 'sometimes|in:default,performance,custom',
            'custom_rate' => 'nullable|numeric|min:0|max:100',
            'bank_name' => 'nullable|max:255',
            'account_number' => 'nullable|max:100',
            'branch_code' => 'nullable|max:50',
            'tax_id' => 'nullable|max:100',
            'status' => 'sometimes|in:active,suspended',
            'password' => 'sometimes|min:6',
        ]);

        if (isset($validated['password'])) {
            $validated['password'] = Hash::make($validated['password']);
        }

        $agent->update($validated);
        return response()->json($agent);
    }

    public function destroy(Agent $agent): JsonResponse
    {
        $agent->delete();
        return response()->json(null, 204);
    }

    public function toggleStatus(Agent $agent): JsonResponse
    {
        $newStatus = $agent->status === 'active' ? 'suspended' : 'active';
        $agent->update(['status' => $newStatus]);
        return response()->json($agent);
    }

    public function stats(): JsonResponse
    {
        $totalAgents = Agent::count();
        $activeAgents = Agent::where('status', 'active')->count();
        $suspendedAgents = Agent::where('status', 'suspended')->count();

        return response()->json([
            'total' => $totalAgents,
            'active' => $activeAgents,
            'suspended' => $suspendedAgents,
        ]);
    }

    public function login(Request $request): JsonResponse
    {
        $request->validate([
            'email' => 'required|email',
            'password' => 'required',
        ]);

        $agent = Agent::where('email', $request->email)->first();

        if (!$agent || !Hash::check($request->password, $agent->password)) {
            throw ValidationException::withMessages([
                'email' => ['The provided credentials are incorrect.'],
            ]);
        }

        if ($agent->status !== 'active') {
            throw ValidationException::withMessages([
                'email' => ['Your account has been suspended. Please contact support.'],
            ]);
        }

        $token = $agent->createToken('agent-mobile')->plainTextToken;

        return response()->json([
            'agent' => $agent,
            'token' => $token,
            'token_type' => 'Bearer',
        ]);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json([
            'message' => 'Logged out successfully',
        ]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json($request->user());
    }

    public function updateDeviceToken(Request $request): JsonResponse
    {
        $request->user()->update([
            'device_token' => $request->token,
        ]);

        return response()->json(['message' => 'Device token updated']);
    }
}