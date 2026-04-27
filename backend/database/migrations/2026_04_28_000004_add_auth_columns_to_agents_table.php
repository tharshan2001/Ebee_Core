<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('agents', function (Blueprint $table) {
            $table->string('password')->nullable()->after('phone');
            $table->rememberToken()->nullable()->after('password');
            $table->string('device_token')->nullable()->after('rememberToken');
        });
    }

    public function down(): void
    {
        Schema::table('agents', function (Blueprint $table) {
            $table->dropColumn(['password', 'remember_token', 'device_token']);
        });
    }
};