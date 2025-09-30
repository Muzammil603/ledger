export default function Spinner({ size = 5 }) {
  const px = `${size * 4}px`;
  return (
    <span
      className="inline-block animate-spin border-2 border-gray-300 border-t-transparent rounded-full align-middle"
      style={{ width: px, height: px }}
      aria-label="Loading"
    />
  );
}