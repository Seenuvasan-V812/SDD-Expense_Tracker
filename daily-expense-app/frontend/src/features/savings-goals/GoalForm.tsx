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
import { createGoal } from './api'

const schema = z.object({
  name: z.string().min(1, 'Name is required').max(100),
  targetAmount: z
    .string()
    .min(1, 'Target amount is required')
    .refine((v) => parseFloat(v) > 0, { message: 'Must be greater than 0' }),
  targetDate: z.string().optional(),
  description: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export default function GoalForm({ open, onOpenChange }: Props) {
  const qc = useQueryClient()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', targetAmount: '', targetDate: '', description: '' },
  })

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      createGoal({
        name: values.name,
        targetAmount: { amount: values.targetAmount, currency: 'INR' },
        targetDate: values.targetDate || undefined,
        description: values.description || undefined,
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
          <DialogTitle>New Savings Goal</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Goal Name</FormLabel>
                  <FormControl>
                    <Input placeholder="Emergency Fund" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="targetAmount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Target Amount (INR)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="50000.00"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="targetDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Target Date (optional)</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description (optional)</FormLabel>
                  <FormControl>
                    <Input placeholder="Save for a rainy day" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {mutation.isError && (
              <p className="text-sm text-destructive">Failed to create goal. Please try again.</p>
            )}

            <DialogFooter>
              <Button
                type="submit"
                disabled={mutation.isPending}
                aria-busy={mutation.isPending}
              >
                {mutation.isPending ? 'Creating…' : 'Create Goal'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
