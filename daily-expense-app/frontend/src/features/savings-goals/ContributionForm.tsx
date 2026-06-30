import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
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
import { recordContribution } from './api'

const schema = z.object({
  amount: z
    .string()
    .min(1, 'Amount must be greater than 0')
    .refine((v) => parseFloat(v) > 0, {
      message: 'Amount must be greater than 0',
    }),
  date: z.string().min(1, 'Date is required'),
})

type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  goalId: string
  goalName: string
}

export default function ContributionForm({
  open,
  onOpenChange,
  goalId,
  goalName,
}: Props) {
  const qc = useQueryClient()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { amount: '', date: '' },
  })

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      recordContribution(goalId, {
        amount: { amount: values.amount, currency: 'INR' },
        date: values.date,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['savings-goals'] })
      onOpenChange(false)
      form.reset()
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Contribution — {goalName}</DialogTitle>
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
            <DialogFooter>
              <Button
                type="submit"
                disabled={mutation.isPending}
                aria-busy={mutation.isPending}
              >
                {mutation.isPending ? 'Recording…' : 'Record Contribution'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
