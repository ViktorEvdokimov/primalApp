import { Button, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router';
import { ru } from '../i18n/ru';

/** Заглушка экрана, который появится в следующих задачах плана. */
export function PageStub({ title, testId }: { title: string; testId: string }) {
  return (
    <Stack gap="md" data-testid={testId}>
      <Title order={2}>{title}</Title>
      <Text c="dimmed">{ru.stub.inProgress}</Text>
      <Button component={Link} to="/" variant="light" data-testid="stub-to-menu">
        {ru.stub.toMenu}
      </Button>
    </Stack>
  );
}
