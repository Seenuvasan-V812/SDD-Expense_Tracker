interface MoneyDisplayProps {
  amount: number
  currency?: string
}

const formatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
})

export default function MoneyDisplay({ amount }: MoneyDisplayProps) {
  return (
    <span className="tabular-nums font-semibold">{formatter.format(amount)}</span>
  )
}
