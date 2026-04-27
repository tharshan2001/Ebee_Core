<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Auth\Authenticatable;
use Illuminate\Contracts\Auth\Authenticatable as AuthenticatableContract;
use Laravel\Sanctum\HasApiTokens;

class Driver extends Model implements AuthenticatableContract
{
    use HasApiTokens, Authenticatable;

    protected $fillable = [
        'name',
        'phone',
        'vehicle_type',
        'status',
        'current_lat',
        'current_lng',
        'password',
        'device_token',
    ];

    protected $hidden = [
        'password',
        'device_token',
    ];

    protected $casts = [
        'current_lat' => 'decimal:7',
        'current_lng' => 'decimal:7',
    ];

    public function deliveries(): HasMany
    {
        return $this->hasMany(Delivery::class);
    }

    public function isAvailable(): bool
    {
        return in_array($this->status, ['online', 'on_duty']);
    }
}