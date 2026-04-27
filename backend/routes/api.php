<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\ProductController;
use App\Http\Controllers\Api\CategoryController;
use App\Http\Controllers\Api\DashboardController;
use App\Http\Controllers\Api\AgentController;
use App\Http\Controllers\Api\OrderController;
use App\Http\Controllers\Api\DriverController;
use App\Http\Controllers\Api\LeadController;

Route::get('dashboard/stats', [DashboardController::class, 'getStats']);
Route::get('dashboard/recent-products', [DashboardController::class, 'getRecentProducts']);
Route::get('products/stats', [ProductController::class, 'stats']);
Route::get('products/search', [ProductController::class, 'advancedSearch']);
Route::patch('products/{product}/archive', [ProductController::class, 'archive']);

Route::apiResource('products', ProductController::class);
Route::get('products/lookup/{sku}', [ProductController::class, 'lookup']);
Route::apiResource('categories', CategoryController::class);

Route::apiResource('agents', AgentController::class);
Route::patch('agents/{agent}/toggle-status', [AgentController::class, 'toggleStatus']);
Route::get('agents/stats', [AgentController::class, 'stats']);

Route::post('agents/login', [AgentController::class, 'login']);
Route::post('agents/register', [AgentController::class, 'store']);

Route::middleware('auth:sanctum')->group(function () {
    Route::get('agents/me', [AgentController::class, 'me']);
    Route::post('agents/logout', [AgentController::class, 'logout']);
    Route::patch('agents/device-token', [AgentController::class, 'updateDeviceToken']);
    Route::post('agents/orders', [OrderController::class, 'placeOrder']);
    Route::get('agents/orders', [OrderController::class, 'myOrders']);
});

Route::apiResource('orders', OrderController::class);
Route::get('orders/{order}/timeline', [OrderController::class, 'timeline']);
Route::post('orders/{order}/assign-driver', [OrderController::class, 'assignDriver']);
Route::post('orders/{order}/resolve-attribution', [OrderController::class, 'resolveAttribution']);
Route::get('orders/stats', [OrderController::class, 'stats']);
Route::get('orders/zones', [OrderController::class, 'zones']);

Route::apiResource('drivers', DriverController::class);
Route::patch('drivers/{driver}/toggle-status', [DriverController::class, 'toggleStatus']);
Route::post('drivers/login', [DriverController::class, 'login']);

Route::middleware('auth:sanctum')->group(function () {
    Route::get('drivers/me/deliveries', [DriverController::class, 'myDeliveries']);
    Route::patch('drivers/location', [DriverController::class, 'updateLocation']);
});

Route::apiResource('leads', LeadController::class);
Route::post('leads/{lead}/convert', [LeadController::class, 'convert']);
Route::get('leads/stats', [LeadController::class, 'stats']);