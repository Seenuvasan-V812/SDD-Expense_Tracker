import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { User, Lock, CheckCircle2 } from 'lucide-react'
import LoadingState from '@/components/LoadingState'
import ErrorState from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { fetchProfile, updateProfile, changePassword } from './api'

const profileSchema = z.object({
  fullName: z.string().min(1, 'Full name is required').max(100),
  timezone: z.string().max(50).optional().default(''),
  locale: z.string().max(10).optional().default(''),
  weeklyDigestEnabled: z.boolean(),
})

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: z.string().min(8, 'At least 8 characters'),
  confirmPassword: z.string().min(1, 'Confirm your new password'),
}).refine((d) => d.newPassword === d.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords do not match',
})

type ProfileValues = z.infer<typeof profileSchema>
type PasswordValues = z.infer<typeof passwordSchema>

export default function ProfilePage() {
  const qc = useQueryClient()
  const [profileSaved, setProfileSaved] = useState(false)
  const [passwordSaved, setPasswordSaved] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  const { data: profile, isLoading, isError, refetch } = useQuery({
    queryKey: ['profile'],
    queryFn: fetchProfile,
  })

  const profileForm = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    values: profile
      ? {
          fullName: profile.fullName,
          timezone: profile.timezone ?? '',
          locale: profile.locale ?? '',
          weeklyDigestEnabled: profile.weeklyDigestEnabled,
        }
      : undefined,
  })

  const passwordForm = useForm<PasswordValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const profileMutation = useMutation({
    mutationFn: (values: ProfileValues) =>
      updateProfile({
        fullName: values.fullName,
        timezone: values.timezone ?? '',
        locale: values.locale ?? '',
        weeklyDigestEnabled: values.weeklyDigestEnabled,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['profile'] })
      setProfileSaved(true)
      setTimeout(() => setProfileSaved(false), 3000)
    },
  })

  const passwordMutation = useMutation({
    mutationFn: (values: PasswordValues) =>
      changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      }),
    onSuccess: () => {
      passwordForm.reset()
      setPasswordError(null)
      setPasswordSaved(true)
      setTimeout(() => setPasswordSaved(false), 3000)
    },
    onError: () => {
      setPasswordError('Failed to change password. Check that your current password is correct.')
    },
  })

  if (isLoading) return <LoadingState />
  if (isError)
    return <ErrorState message="Failed to load profile" onRetry={() => void refetch()} />

  return (
    <div className="space-y-6 p-4 max-w-2xl">
      <h1 className="text-2xl font-semibold">Profile</h1>

      {/* Account info card */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <User className="h-4 w-4" />
            Account Information
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Email</span>
            <span className="font-medium">{profile?.email}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Status</span>
            <Badge variant={profile?.status === 'ACTIVE' ? 'default' : 'secondary'}>
              {profile?.status}
            </Badge>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Currency</span>
            <span>{profile?.preferredCurrency ?? 'INR'}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Member since</span>
            <span>
              {profile?.createdAt
                ? new Date(profile.createdAt).toLocaleDateString('en-IN', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })
                : '—'}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Edit profile card */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Edit Profile</CardTitle>
        </CardHeader>
        <CardContent>
          <Form {...profileForm}>
            <form
              onSubmit={profileForm.handleSubmit((v) => profileMutation.mutate(v))}
              className="space-y-4"
            >
              <FormField
                control={profileForm.control}
                name="fullName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Full Name</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={profileForm.control}
                name="timezone"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Timezone</FormLabel>
                    <FormControl>
                      <Input placeholder="Asia/Kolkata" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={profileForm.control}
                name="locale"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Locale</FormLabel>
                    <FormControl>
                      <Input placeholder="en-IN" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={profileForm.control}
                name="weeklyDigestEnabled"
                render={({ field }) => (
                  <FormItem className="flex items-center justify-between">
                    <FormLabel>Weekly Digest Email</FormLabel>
                    <FormControl>
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                        aria-label="Weekly digest"
                      />
                    </FormControl>
                  </FormItem>
                )}
              />

              {profileSaved && (
                <Alert role="status">
                  <CheckCircle2 className="h-4 w-4" />
                  <AlertDescription>Profile updated successfully.</AlertDescription>
                </Alert>
              )}

              <Button
                type="submit"
                disabled={profileMutation.isPending}
                aria-busy={profileMutation.isPending}
              >
                {profileMutation.isPending ? 'Saving…' : 'Save Changes'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>

      {/* Change password card */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <Lock className="h-4 w-4" />
            Change Password
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Form {...passwordForm}>
            <form
              onSubmit={passwordForm.handleSubmit((v) => {
                setPasswordError(null)
                passwordMutation.mutate(v)
              })}
              className="space-y-4"
            >
              <FormField
                control={passwordForm.control}
                name="currentPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Current Password</FormLabel>
                    <FormControl>
                      <Input type="password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={passwordForm.control}
                name="newPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>New Password</FormLabel>
                    <FormControl>
                      <Input type="password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={passwordForm.control}
                name="confirmPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Confirm New Password</FormLabel>
                    <FormControl>
                      <Input type="password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {passwordError && (
                <Alert variant="destructive" role="alert">
                  <AlertDescription>{passwordError}</AlertDescription>
                </Alert>
              )}
              {passwordSaved && (
                <Alert role="status">
                  <CheckCircle2 className="h-4 w-4" />
                  <AlertDescription>Password changed successfully.</AlertDescription>
                </Alert>
              )}

              <Button
                type="submit"
                disabled={passwordMutation.isPending}
                aria-busy={passwordMutation.isPending}
              >
                {passwordMutation.isPending ? 'Changing…' : 'Change Password'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
