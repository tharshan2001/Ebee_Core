<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('orders', function (Blueprint $table) {
            $table->id();
            $table->string('order_number')->unique();
            $table->foreignId('customer_id')->nullable()->constrained('users')->nullOnDelete('set null');
            $table->string('customer_name');
            $table->string('customer_phone');
            $table->string('customer_email')->nullable();
            $table->string('customer_address');
            $table->string('delivery_zone')->nullable();
            $table->string('agent_code_used')->nullable();
            $table->foreign('agent_code_used')->references('code')->on('agents')->nullOnDelete('set null');
            $table->decimal('total_value', 12, 2);
            $table->string('product_name')->nullable();
            $table->string('product_sku')->nullable();
            $table->enum('status', [
                'pending',
                'processing',
                'assigned',
                'out_for_delivery',
                'delivered',
                'commission_locked',
                'commission_released',
                'paid',
                'returned',
                'cancelled'
            ])->default('pending');
            $table->string('attribution_note')->nullable();
            $table->string('attribution_resolved_by')->nullable();
            $table->timestamp('attribution_resolved_at')->nullable();
            $table->timestamps();
        });

        Schema::create('deliveries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('order_id')->constrained()->cascadeOnDelete();
            $table->foreignId('driver_id')->nullable()->constrained('drivers')->nullOnDelete('set null');
            $table->enum('status', [
                'pending',
                'assigned',
                'picked_up',
                'en_route',
                'arrived',
                'delivered',
                'failed'
            ])->default('pending');
            $table->timestamp('assigned_at')->nullable();
            $table->timestamp('picked_up_at')->nullable();
            $table->timestamp('delivered_at')->nullable();
            $table->string('photo_url')->nullable();
            $table->string('signature_url')->nullable();
            $table->string('serial_number')->nullable();
            $table->text('driver_notes')->nullable();
            $table->string('failure_reason')->nullable();
            $table->decimal('driver_lat', 10, 7)->nullable();
            $table->decimal('driver_lng', 10, 7)->nullable();
            $table->timestamps();
        });

        Schema::create('commissions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('agent_id')->constrained()->cascadeOnDelete();
            $table->foreignId('order_id')->constrained()->cascadeOnDelete();
            $table->decimal('amount', 12, 2);
            $table->decimal('rate_used', 5, 2);
            $table->enum('status', [
                'pending',
                'locked',
                'payable',
                'paid',
                'reversed'
            ])->default('pending');
            $table->timestamp('locked_at')->nullable();
            $table->timestamp('released_at')->nullable();
            $table->timestamp('paid_at')->nullable();
            $table->string('payout_batch_id')->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
        });

        Schema::create('leads', function (Blueprint $table) {
            $table->id();
            $table->foreignId('agent_id')->constrained()->cascadeOnDelete();
            $table->string('customer_name');
            $table->string('customer_phone');
            $table->string('customer_email')->nullable();
            $table->string('product_interest')->nullable();
            $table->text('notes')->nullable();
            $table->enum('status', ['pending', 'converted', 'expired'])->default('pending');
            $table->foreignId('converted_order_id')->nullable()->constrained('orders')->nullOnDelete('set null');
            $table->timestamp('converted_at')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('leads');
        Schema::dropIfExists('commissions');
        Schema::dropIfExists('deliveries');
        Schema::dropIfExists('orders');
    }
};