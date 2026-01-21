<template>
    <button class="base-button">
        <!--slot이란? 자리표시자-->
        <slot></slot>
    </button>
</template>

<style scoped>
    .base-button {
        padding: 12px 20px;
        border-radius: 8px;
        background: var(--color-primary);
        color: white;
        border: none;
    }
</style>