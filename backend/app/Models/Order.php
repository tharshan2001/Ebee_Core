<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Order extends Model
{
    protected $fillable = [
        'order_number',
        'customer_id',
        'customer_name',
        'customer_phone',
        'customer_email',
        'customer_address',
        'delivery_zone',
        'agent_code_used',
        'total_value',
        'product_name',
        'product_sku',
        'status',
        'attribution_note',
        'attribution_resolved_by',
        'attribution_resolved_at',
    ];

    protected $casts = [
        'total_value' => 'decimal:2',
        'attribution_resolved_at' => 'datetime',
    ];

    protected static function boot()
    {
        parent::boot();
        static::creating(function ($order) {
            if (empty($order->order_number)) {
                $order->order_number = 'ORD-' . date('Ymd') . '-' . strtoupper(uniqid());
            }
        });
    }

    public function agent(): BelongsTo
    {
        return $this->belongsTo(Agent::class, 'agent_code_used', 'code');
    }

    public function customer(): BelongsTo
    {
        return $this->belongsTo(User::class, 'customer_id');
    }

    public function delivery(): HasMany
    {
        return $this->hasOne(Delivery::class);
    }

    public function commission(): HasMany
    {
        return $this->hasOne(Commission::class);
    }

    public function getTimelineAttribute(): array
    {
        $timeline = [];
        $timeline[] = ['status' => 'ordered', 'timestamp' => $this->created_at];

        if (in_array($this->status, ['processing', 'assigned', 'out_for_delivery', 'delivered', 'commission_locked', 'commission_released', 'paid'])) {
            $timeline[] = ['status' => 'processing', 'timestamp' => $this->updated_at];
        }

        if (in_array($this->status, ['assigned', 'out_for_delivery', 'delivered', 'commission_locked', 'commission_released', 'paid']) && $this->delivery) {
            $timeline[] = ['status' => 'assigned_to_driver', 'timestamp' => $this->delivery->assigned_at, 'driver' => $this->delivery->driver_id];
        }

        if (in_array($this->status, ['out_for_delivery', 'delivered', 'commission_locked', 'commission_released', 'paid']) && $this->delivery) {
            $timeline[] = ['status' => 'en_route', 'timestamp' => $this->delivery->picked_up_at];
        }

        if (in_array($this->status, ['delivered', 'commission_locked', 'commission_released', 'paid']) && $this->delivery) {
            $timeline[] = ['status' => 'delivered', 'timestamp' => $this->delivery->delivered_at, 'photo' => $this->delivery->photo_url];
        }

        if (in_array($this->status, ['commission_locked', 'commission_released', 'paid']) && $this->commission) {
            $timeline[] = ['status' => 'commission_locked', 'timestamp' => $this->commission->locked_at];
        }

        if (in_array($this->status, ['commission_released', 'paid']) && $this->commission) {
            $timeline[] = ['status' => 'commission_released', 'timestamp' => $this->commission->released_at];
        }

        if ($this->status === 'paid' && $this->commission) {
            $timeline[] = ['status' => 'payout_processed', 'timestamp' => $this->commission->paid_at];
        }

        return $timeline;
    }
}