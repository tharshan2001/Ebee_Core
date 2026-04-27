<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Config extends Model
{
    protected $fillable = [
        'key',
        'value',
        'type',
        'description',
    ];

    public static function get($key, $default = null)
    {
        $config = static::where('key', $key)->first();
        
        if (!$config) {
            return $default;
        }

        return match($config->type) {
            'integer' => (int) $config->value,
            'float' => (float) $config->value,
            'boolean' => filter_var($config->value, FILTER_VALIDATE_BOOLEAN),
            'json' => json_decode($config->value, true),
            default => $config->value,
        };
    }

    public static function set($key, $value, $type = 'string', $description = null)
    {
        return static::updateOrCreate(
            ['key' => $key],
            [
                'value' => is_array($value) || is_object($value) ? json_encode($value) : (string) $value,
                'type' => $type,
                'description' => $description,
            ]
        );
    }

    public static function defaultConfigs()
    {
        return [
            ['key' => 'commission_default_rate', 'value' => '5', 'type' => 'float', 'description' => 'Default commission rate percentage'],
            ['key' => 'commission_performance_rate', 'value' => '7', 'type' => 'float', 'description' => 'Performance tier commission rate'],
            ['key' => 'commission_performance_threshold', 'value' => '10000', 'type' => 'float', 'description' => 'Monthly threshold for performance tier'],
            ['key' => 'commission_lock_days', 'value' => '10', 'type' => 'integer', 'description' => 'Days to lock commission after delivery'],
            ['key' => 'app_name', 'value' => 'Ebee', 'type' => 'string', 'description' => 'Application name'],
            ['key' => 'app_currency', 'value' => 'LKR', 'type' => 'string', 'description' => 'Currency code'],
            ['key' => 'app_currency_symbol', 'value' => 'Rs.', 'type' => 'string', 'description' => 'Currency symbol'],
        ];
    }

    public static function seedDefaults()
    {
        foreach (static::defaultConfigs() as $config) {
            static::updateOrCreate(
                ['key' => $config['key']],
                $config
            );
        }
    }
}