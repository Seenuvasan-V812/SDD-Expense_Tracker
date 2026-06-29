import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { createExpense, updateExpense } from './api'
import { fetchCategories } from '@/features/categories/api'
import type { ExpenseResponse } from './types'

const MAX_RECEIPT_BYTES = 5 * 1024 * 1024
const ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp']

const schema = z.object({
  amount: z
    .string()
    .min(1, 'Amount must be greater than 0')
    .refine((v) => parseFloat(v) > 0, { message: 'Amount must be greater than 0' }),
  date: z.string().min(1, 'Date is required'),
  categoryId: z.string().min(1, 'Category is required'),
  paymentMethod: z.enum([
    'UPI',
    'CASH',
    'CREDIT_CARD',
    'DEBIT_CARD',
    'NET_BANKING',
    'OTHER',
  ] as const),
  description: z.string().optional(),
  merchant: z.string().optional(),
  notes: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  expense?: ExpenseResponse
}

export default function ExpenseForm({ open, onOpenChange, expense }: Props) {
  const qc = useQueryClient()
  const [receiptError, setReceiptError] = useState<string | null>(null)
  const [receiptFile, setReceiptFile] = useState<File | null>(null)

  const { data: catData } = useQuery({
    queryKey: ['categories', 'ALL', 0],
    queryFn: () => fetchCategories({ page: 0 }),
  })

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      amount: expense?.amount.amount ?? '',
      date: expense?.date ?? '',
      categoryId: expense?.categoryId ?? '',
      paymentMethod: expense?.paymentMethod ?? 'UPI',
      description: expense?.description ?? '',
      merchant: expense?.merchant ?? '',
      notes: expense?.notes ?? '',
    },
  })

  const mutation = useMutation({
    mutationFn: (values: FormValues) => {
      const req = {
        amount: { amount: values.amount, currency: 'INR' },
        date: values.date,
        categoryId: values.categoryId,
        paymentMethod: values.paymentMethod,
        description: values.description || undefined,
        merchant: values.merchant || undefined,
        notes: values.notes || undefined,
      }
      return expense
        ? updateExpense(expense.expenseId, req)
        : createExpense(req)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['expenses'] })
      onOpenChange(false)
      setReceiptFile(null)
      setReceiptError(null)
    },
  })

  const handleReceiptChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setReceiptError(null)

    if (file.size > MAX_RECEIPT_BYTES) {
      setReceiptError('Receipt file must be ≤ 5 MB')
      e.target.value = ''
      return
    }
    if (!ALLOWED_MIME.includes(file.type)) {
      setReceiptError('Receipt must be JPEG, PNG, or WEBP')
      e.target.value = ''
      return
    }
    setReceiptFile(file)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{expense ? 'Edit Expense' : 'Add Expense'}</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Amount (INR)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="0.00"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="date"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="categoryId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Category</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger aria-label="Expense category">
                        <SelectValue placeholder="Select category" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {catData?.content.map((c) => (
                        <SelectItem key={c.categoryId} value={c.categoryId}>
                          {c.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="paymentMethod"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment Method</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger aria-label="Payment method">
                        <SelectValue placeholder="Select method" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="UPI">UPI</SelectItem>
                      <SelectItem value="CASH">Cash</SelectItem>
                      <SelectItem value="CREDIT_CARD">Credit Card</SelectItem>
                      <SelectItem value="DEBIT_CARD">Debit Card</SelectItem>
                      <SelectItem value="NET_BANKING">Net Banking</SelectItem>
                      <SelectItem value="OTHER">Other</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Input placeholder="Optional description" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="merchant"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Merchant</FormLabel>
                  <FormControl>
                    <Input placeholder="Optional merchant" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="space-y-2">
              <label htmlFor="receipt" className="text-sm font-medium leading-none">
                Receipt
              </label>
              <input
                id="receipt"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                aria-label="Receipt"
                onChange={handleReceiptChange}
                className="block w-full text-sm file:mr-4 file:rounded file:border-0 file:bg-primary file:px-3 file:py-1.5 file:text-sm file:text-primary-foreground"
              />
              {receiptFile && (
                <p className="text-xs text-muted-foreground">{receiptFile.name}</p>
              )}
              {receiptError && (
                <Alert variant="destructive" role="alert">
                  <AlertDescription>{receiptError}</AlertDescription>
                </Alert>
              )}
            </div>

            {mutation.isError && (
              <Alert variant="destructive" role="alert">
                <AlertDescription>Failed to save expense. Please try again.</AlertDescription>
              </Alert>
            )}

            <DialogFooter>
              <Button
                type="submit"
                disabled={mutation.isPending}
                aria-busy={mutation.isPending}
              >
                {mutation.isPending ? 'Saving…' : 'Save Expense'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
