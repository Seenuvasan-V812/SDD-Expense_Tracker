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
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { createBudget } from './api'
import { fetchCategories } from '@/features/categories/api'

const schema = z.object({
  scope: z.enum(['OVERALL', 'CATEGORY'] as const),
  categoryId: z.string().optional(),
  limit: z
    .string()
    .min(1, 'Limit must be greater than 0')
    .refine((v) => parseFloat(v) > 0, {
      message: 'Limit must be greater than 0',
    }),
  period: z.enum(['WEEKLY', 'MONTHLY'] as const),
  rolloverEnabled: z.boolean(),
})

type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export default function BudgetForm({ open, onOpenChange }: Props) {
  const qc = useQueryClient()

  const { data: catData } = useQuery({
    queryKey: ['categories', 'ALL', 0],
    queryFn: () => fetchCategories({ page: 0 }),
  })

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      scope: 'OVERALL',
      categoryId: '',
      limit: '',
      period: 'MONTHLY',
      rolloverEnabled: false,
    },
  })

  const scope = form.watch('scope')

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      createBudget({
        scope: values.scope,
        categoryId: values.scope === 'CATEGORY' ? values.categoryId : undefined,
        limit: { amount: values.limit, currency: 'INR' },
        period: values.period,
        rolloverEnabled: values.rolloverEnabled,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['budgets'] })
      onOpenChange(false)
      form.reset()
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create Budget</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="scope"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Scope</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger aria-label="Budget scope">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="OVERALL">Overall</SelectItem>
                      <SelectItem value="CATEGORY">Category</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {scope === 'CATEGORY' && (
              <FormField
                control={form.control}
                name="categoryId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Category</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger aria-label="Budget category">
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
            )}

            <FormField
              control={form.control}
              name="limit"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Limit (INR)</FormLabel>
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
              name="period"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Period</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger aria-label="Budget period">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="MONTHLY">Monthly</SelectItem>
                      <SelectItem value="WEEKLY">Weekly</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="rolloverEnabled"
              render={({ field }) => (
                <FormItem className="flex items-center justify-between">
                  <FormLabel>Enable Rollover</FormLabel>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      aria-label="Rollover enabled"
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="submit"
                disabled={mutation.isPending}
                aria-busy={mutation.isPending}
              >
                {mutation.isPending ? 'Creating…' : 'Create Budget'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
