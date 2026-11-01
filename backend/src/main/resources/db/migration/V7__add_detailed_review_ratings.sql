-- V7: Detailed vehicle condition, cleanliness, and comfort ratings with booking uniqueness
ALTER TABLE reviews
    ADD COLUMN condition_rating DECIMAL(3,2) NULL,
    ADD COLUMN cleanliness_rating DECIMAL(3,2) NULL,
    ADD COLUMN comfort_rating DECIMAL(3,2) NULL;

-- Enforce exactly one review per booking
ALTER TABLE reviews
    ADD CONSTRAINT uk_reviews_booking UNIQUE (booking_id);

-- Synchronize vehicle ratings based strictly on actual submitted customer reviews
UPDATE vehicles v
LEFT JOIN (
    SELECT vehicle_id, COUNT(*) AS review_count, AVG(vehicle_rating) AS avg_rating
    FROM reviews
    GROUP BY vehicle_id
) r ON v.id = r.vehicle_id
SET v.total_ratings = COALESCE(r.review_count, 0),
    v.average_rating = r.avg_rating;

