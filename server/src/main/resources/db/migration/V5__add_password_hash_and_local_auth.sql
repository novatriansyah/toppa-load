-- V5: Add password_hash column to users table for Hybrid Authentication (Break-Glass Admin & Local Form Login)
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

