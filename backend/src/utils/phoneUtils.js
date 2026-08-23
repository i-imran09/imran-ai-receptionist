export const normalizePhoneNumber = (number) => {
  let normalized = number.replace(/\D/g, '');
  
  if (normalized.length === 10) {
    normalized = '91' + normalized;
  }
  
  return normalized;
};
