<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Laravel\Sanctum\HasApiTokens;
use Illuminate\Auth\Authenticatable;
use Illuminate\Contracts\Auth\Authenticatable as AuthenticatableContract;

class Agent extends Model implements AuthenticatableContract
{
    use HasApiTokens, Authenticatable;

    protected $fillable = [
        'name',
        'email',
        'phone',
        'code',
        'referral_link',
        'commission_tier',
        'custom_rate',
        'bank_name',
        'account_number',
        'branch_code',
        'tax_id',
        'status',
        'password',
        'device_token',
    ];

    protected $hidden = [
        'password',
        'device_token',
    ];

    protected $casts = [
        'custom_rate' => 'decimal:2',
        'email_verified_at' => 'datetime',
    ];

    public function orders(): HasMany
    {
        return $this->hasMany(Order::class, 'agent_code_used', 'code');
    }

    public function commissions(): HasMany
    {
        return $this->hasMany(Commission::class);
    }

    public function getCommissionRateAttribute(): float
    {
        if ($this->commission_tier === 'custom' && $this->custom_rate) {
            return (float) $this->custom_rate;
        }

        if ($this->commission_tier === 'performance') {
            return 7.0;
        }

        return 5.0;
    }
}