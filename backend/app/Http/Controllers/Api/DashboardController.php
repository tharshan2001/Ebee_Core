<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Product;
use App\Models\Category;
use Illuminate\Http\Request;

class DashboardController extends Controller
{
    public function getStats()
    {
        $totalProducts = Product::count();
        $activeProducts = Product::where('archived', false)->count();
        $archivedProducts = Product::where('archived', true)->count();
        $categories = Category::count();

        $priceStats = Product::where('archived', false)
            ->selectRaw('MIN(price) as minPrice, MAX(price) as maxPrice, AVG(price) as avgPrice')
            ->first();

        return response()->json([
            'totalProducts' => $totalProducts,
            'activeProducts' => $activeProducts,
            'archivedProducts' => $archivedProducts,
            'categories' => $categories,
            'minPrice' => $priceStats->minPrice ?? 0,
            'maxPrice' => $priceStats->maxPrice ?? 0,
            'avgPrice' => $priceStats->avgPrice ?? 0,
        ]);
    }

    public function getRecentProducts()
    {
        $products = Product::orderBy('created_at', 'desc')
            ->limit(5)
            ->get();

        return response()->json($products);
    }

    public function getProductStats()
    {
        $stats = Product::where('archived', false)
            ->selectRaw('MIN(price) as minPrice, MAX(price) as maxPrice, COUNT(*) as count')
            ->first();

        return response()->json($stats ?? ['minPrice' => 0, 'maxPrice' => 0, 'count' => 0]);
    }
}