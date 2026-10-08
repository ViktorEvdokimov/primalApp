import { Anchor, Stack, Text } from '@mantine/core';
import { Link } from 'react-router';
import type { InfoEntry } from '../../api/generated/primal.schemas';
import { entryHref, paragraphs, segments } from './infoModel';

interface InfoTextProps {
  body: string;
  resolve: (reference: string) => InfoEntry | null;
}

/** Текст статьи: абзацы, **жирный**, «См. также «…»» — ссылки на статьи (qa № 142). */
export function InfoText({ body, resolve }: InfoTextProps) {
  return (
    <Stack gap="xs">
      {paragraphs(body).map((paragraph, index) => (
        <Text key={index} size="sm" style={{ whiteSpace: 'pre-line' }}>
          {segments(paragraph, resolve).map((segment, part) => {
            if (segment.kind === 'bold') return <strong key={part}>{segment.text}</strong>;
            if (segment.kind === 'link') {
              return (
                <Anchor
                  key={part}
                  component={Link}
                  to={entryHref(segment.entry)}
                  data-testid="info-link"
                  data-entry={segment.entry.id}
                >
                  {segment.text}
                </Anchor>
              );
            }
            return segment.text;
          })}
        </Text>
      ))}
    </Stack>
  );
}
