import { format, parseISO } from 'date-fns'
import { enIN } from 'date-fns/locale'

interface DateDisplayProps {
  dateStr: string
  pattern?: string
}

export default function DateDisplay({ dateStr, pattern = 'dd MMM yyyy' }: DateDisplayProps) {
  const date = parseISO(dateStr)
  return <span>{format(date, pattern, { locale: enIN })}</span>
}
