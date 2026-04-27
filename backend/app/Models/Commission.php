<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Commission extends Model
{
    protected $fillable = [
        'agent_id',
        'order_id',
        'amount',
        'rate_used',
        'status',
        'locked_at',
        'released_at',
        'paid_at',
        'payout_batch_id',
        'notes',
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'rate_used' => 'decimal:2',
        'locked_at' => 'datetime',
        'released_at' => 'datetime',
        'paid_at' => 'datetime',
    ];

    public function agent(): BelongsTo
    {
        return $this->belongsTo(Agent::class);
    }

    public function order(): BelongsTo
    {
        return $this->belongsTo(Order::class);
    }

    public function getIsLockedAttribute(): bool
    {
        return $this->status === 'locked' && 
               $this->locked_at && 
               now()->lt($this->locked_at->addDays(10));
    }

    public function getIsReleasableAttribute(): bool
    {
        return $this->status === 'locked' && 
               $this->locked_at && 
               now()->greaterThanOrEqualTo($this->locked_at->addDays(10));
    }
}