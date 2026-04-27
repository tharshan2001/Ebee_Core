<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Product;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class ProductController extends Controller
{
    public function index(Request $request)
    {
        $query = Product::with('category');

        if ($request->has('search') && $request->search) {
            $search = $request->search;
            $query->where(function ($q) use ($search) {
                $q->where('name', 'ilike', "%{$search}%")
                    ->orWhere('code', 'ilike', "{$search}%")
                    ->orWhereJsonContains('tags', $search);
            });
        }

        if ($request->has('category_id') && $request->category_id) {
            $query->where('category_id', $request->category_id);
        }

        if ($request->has('min_price')) {
            $query->where('price', '>=', $request->min_price);
        }

        if ($request->has('max_price')) {
            $query->where('price', '<=', $request->max_price);
        }

        if ($request->has('archived')) {
            $query->where('archived', $request->boolean('archived'));
        } else {
            $query->where('archived', false);
        }

        $sortBy = $request->sortBy ?? 'id';
        $sortOrder = $request->sortOrder ?? 'desc';
        $query->orderBy($sortBy, $sortOrder);

        $limit = $request->limit ?? 20;
        $products = $query->paginate($limit);

        return response()->json($products);
    }

    public function store(Request $request)
    {
        $validated = $request->validate([
            'code' => 'required|unique:products',
            'name' => 'required',
            'slug' => 'sometimes|unique:products',
            'image_url' => 'nullable',
            'tags' => 'nullable|array',
            'category_id' => 'nullable|exists:categories,id',
            'archived' => 'boolean',
            'price' => 'required|numeric|min:0',
        ]);

        if (!$request->has('slug') || !$request->slug) {
            $validated['slug'] = Str::slug($request->name);
            $counter = 1;
            while (Product::where('slug', $validated['slug'])->exists()) {
                $validated['slug'] = Str::slug($request->name) . '-' . $counter;
                $counter++;
            }
        }

        $product = Product::create($validated);
        return response()->json($product, 201);
    }

    public function show(Product $product)
    {
        return response()->json($product->load('category'));
    }

    public function update(Request $request, Product $product)
    {
        $validated = $request->validate([
            'code' => 'sometimes|unique:products,code,' . $product->id,
            'name' => 'sometimes',
            'slug' => 'sometimes|unique:products,slug,' . $product->id,
            'image_url' => 'nullable',
            'tags' => 'nullable|array',
            'category_id' => 'nullable|exists:categories,id',
            'archived' => 'boolean',
            'price' => 'sometimes|numeric|min:0',
        ]);

        $product->update($validated);
        return response()->json($product->fresh('category'));
    }

    public function destroy(Product $product)
    {
        $product->delete();
        return response()->json(null, 204);
    }

    public function archive(Request $request, Product $product)
    {
        $validated = $request->validate([
            'archived' => 'required|boolean',
        ]);

        $product->update(['archived' => $validated['archived']]);
        $status = $validated['archived'] ? 'archived' : 'unarchived';
        return response()->json(['message' => "Product {$status}", 'product' => $product]);
    }

    public function advancedSearch(Request $request)
    {
        $query = Product::query();

        if ($request->has('search') && $request->search) {
            $search = $request->search;
            $query->where(function ($q) use ($search) {
                $q->where('name', 'ilike', "%{$search}%")
                    ->orWhere('code', 'ilike', "{$search}%")
                    ->orWhere('slug', 'ilike', "%{$search}%")
                    ->orWhereJsonContains('tags', $search);
            });
        }

        if ($request->has('category_id') && $request->category_id) {
            $query->where('category_id', $request->category_id);
        }

        if ($request->has('tags') && $request->tags) {
            $tags = explode(',', $request->tags);
            $query->where(function ($q) use ($tags) {
                foreach ($tags as $tag) {
                    $q->orWhereJsonContains('tags', trim($tag));
                }
            });
        }

        if ($request->has('min_price')) {
            $query->where('price', '>=', $request->min_price);
        }

        if ($request->has('max_price')) {
            $query->where('price', '<=', $request->max_price);
        }

        if ($request->has('archived')) {
            $query->where('archived', $request->boolean('archived'));
        }

        $sortBy = $request->sortBy ?? 'id';
        $sortOrder = $request->sortOrder ?? 'desc';
        $query->orderBy($sortBy, $sortOrder);

        $limit = $request->limit ?? 12;
        $products = $query->paginate($limit);

        return response()->json($products);
    }

    public function stats()
    {
        $stats = Product::where('archived', false)
            ->selectRaw('MIN(price) as minPrice, MAX(price) as maxPrice, AVG(price) as avgPrice, COUNT(*) as count')
            ->first();

        return response()->json($stats ?? ['minPrice' => 0, 'maxPrice' => 0, 'avgPrice' => 0, 'count' => 0]);
    }

    public function lookup($sku)
    {
        $product = Product::where('code', $sku)
            ->where('archived', false)
            ->first();

        if (!$product) {
            return response()->json(['error' => 'Product not found'], 404);
        }

        return response()->json([
            'sku' => $product->code,
            'name' => $product->name,
            'price' => $product->price,
            'image_url' => $product->image_url,
            'category' => $product->category?->name,
        ]);
    }
}