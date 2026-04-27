<?php

use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return response()->json([
        'status' => 'ok',
        'message' => 'POS System API is running',
        'version' => '1.0.0'
    ]);
});
