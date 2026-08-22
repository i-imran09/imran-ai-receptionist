export function normalizePhoneNumber(phoneNumber) {
  // Remove all non-digit characters
  let normalized = phoneNumber.replace(/\D/g, '');

  // If it starts with country code, keep it
  // Otherwise, assume it's just the number without country code
  if (normalized.length > 10 && normalized.length <= 15) {
    // Looks like international format
    return normalized;
  }

  if (normalized.length === 10) {
    // Assume India if 10 digits
    // You can modify this based on your region
    return '91' + normalized;
  }

  // Return as-is if uncertain
  return normalized;
}
